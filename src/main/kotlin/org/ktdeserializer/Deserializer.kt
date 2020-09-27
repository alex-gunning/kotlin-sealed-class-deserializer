package org.ktdeserializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

class Deserializer<T : Any>(private val type: KClass<T>) : JsonDeserializer<T>() {
    override fun deserialize(parser: JsonParser, ctx: DeserializationContext): T {
        val sealedSubclasses = type::sealedSubclasses.get()
        val constructors = sealedSubclasses.map { it.constructors }.map { it.first() }

        val mapper = parser.codec as ObjectMapper
        val node: JsonNode = mapper.readTree(parser)

        // Incoming deserialized fields - Create args list
        val args: List<Pair<String, Any>> = node.fields().asSequence().toHashSet().map {
            when (it.value.nodeType) {
                JsonNodeType.NUMBER -> Pair(it.key, it.value.intValue())
                JsonNodeType.STRING -> Pair(it.key, it.value.textValue())
                JsonNodeType.BOOLEAN -> Pair(it.key, it.value.booleanValue())
                JsonNodeType.OBJECT -> TODO("Recursive step for object")
                JsonNodeType.ARRAY -> TODO("Iterative step for array")
                else -> throw Error("Deserialized data type not yet supported.")
            }
        }.toList()

        // Find out which constructor the supplied args match
        val argNames = args.map { it.first }
        val correctConstructor = findCorrectConstructor(constructors, argNames)

        // then find params which were supplied
        val argsToBePassed = args
                .map { Pair(correctConstructor.parameters.find { p -> p.name == it.first }, it.second) }
                .fold(mapOf<KParameter, Any?>()){ map, param -> map + Pair(param.first!!, param.second)}

        // and pass to the constructor as named arguments. Kotlin will handle defaults
        return correctConstructor.callBy(argsToBePassed)
    }

    private fun findCorrectConstructor(
            constructorList: List<KFunction<T>>,
            suppliedParamList: List<String>): KFunction<T> {
        val constructorParamList = constructorList
                .map { getParameterListWithOptionality(it) }
                .map { withLexicographicalOrdering(it) }
        val constructorParameterNames = constructorParamList
                .map { onlyParamNames(it) }

        /*  Remove supplied params from each constructor in the list.
        *   Return null if a constructor does not have a parameter which has been supplied.
        */
        val constructorsMinusSuppliedParams: List<List<String>?> =
                constructorParameterNames.map {
                    removeAll(it, suppliedParamList)
                }

        /*  Associate the actual constructor with the still-unsupplied params
        *   and remove any null (invalid) constructors.
        */
        val validConstructorsWithoutDefaults = constructorsMinusSuppliedParams
                .zip(constructorList)
                .mapNotNull { if (it.first == null) null else it }
                .map { Pair(it.first!!, it.second) }

        /*  Check if remaining params are optional.
        *   Associate with the constructor and filter the valid ones.
        *   Return just the valid constructors.
        */
        val validConstructors = validConstructorsWithoutDefaults
                .map { checkSuppliedParamsOptional(it) }
                .zip(validConstructorsWithoutDefaults)
                .map { Pair(it.first, it.second.second) }
                .filter { it.first }
                .map { it.second }

        // We may now have more than one completely valid constructor, just choose the first one as a tiebreaker.
        return validConstructors.first()
    }

    private fun getParameterListWithOptionality(constructor: KFunction<T>): List<Pair<String, Boolean>> =
            constructor.parameters.map { Pair(it.name!!, it.isOptional) }

    private fun withLexicographicalOrdering(strList: List<Pair<String, Boolean>>): List<Pair<String, Boolean>> = strList.sortedBy { it.first }

    private fun onlyParamNames(params: List<Pair<String, Boolean>>): List<String> =
            params.map { it.first }

    private fun removeIfExists(haystack: List<String>, needle: String): List<String>? {
        return if (haystack.find { it == needle } != null) {
            haystack.filterNot { it == needle }
        } else {
            null
        }
    }

    // Returns null if a needle does not exist.
    private fun removeAll(haystack: List<String>?, needles: List<String>): List<String>? {
        if (haystack == null) {
            return null
        }
        if (needles.isEmpty()) {
            return haystack
        }
        val newHaystack = removeIfExists(haystack, needles.first())
        return removeAll(newHaystack, needles.slice(1..needles.lastIndex))
    }

    private fun checkSuppliedParamsOptional(paramPair: Pair<List<String>, KFunction<T>>): Boolean {
        val unsuppliedParams = paramPair.first
        val constructor = paramPair.second

        val areParamsOptional = unsuppliedParams.map { param ->
            constructor.parameters.find { cparam -> cparam.name == param }?.isOptional
        }.fold(true) { a, b -> a && (b ?: true) }
        return areParamsOptional
    }

}
