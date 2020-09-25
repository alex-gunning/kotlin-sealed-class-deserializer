package org.ktdeserializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class Deserializer<T : Any>(private val type: KClass<T>) : JsonDeserializer<T>() {
    data class Param(val type: String, val name: String, val nullable: Boolean? = null, val value: Any? = null)

    fun Param.matches(other: Param): Boolean =
            this.type == other.type && this.name == other.name && this.nullable == other.nullable

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
        val correctConstructor = findCorrectConstructor(constructors, args.map { it.first })

        // First find params which were not passed
        val missingArgs = correctConstructor.parameters
                .filterNot { cParam -> args.find { a -> a.first == cParam.name } != null }
                .map { Pair(it.index, null) }

        // then find params which were supplied
        val suppliedArgs = args
                .map { Pair(correctConstructor.parameters.find { p -> p.name == it.first }!!.index, it.second) }
                .sortedBy {
                    it.first
                }

        // merge them in the correct order
        val argsToBePassed = suppliedArgs
                .union(missingArgs)
                .sortedBy { it.first }
                .map { it.second }

        return correctConstructor.call(*argsToBePassed.toTypedArray())
    }

    private fun findCorrectConstructor(
            constructorList: List<KFunction<T>>,
            suppliedParamList: List<String>): KFunction<T> {
        val constructorParamList = constructorList
                .map { getParameterListWithOptionality(it) }
                .map { withLexicographicOrdering(it) }
        val constructorParameterNames = constructorParamList
                .map { onlyParamNames(it) }

        /*  Remove supplied params from each constructor in the list.
        *   Null if a constructor does not have a parameter which has been supplied.
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
                .mapNotNull{ if(it.first == null) null else it }
                .map{ Pair(it.first!!, it.second)}

        /*  Check if remaining params are optional.
        *   Associate with the constructor and filter the valid ones.
        */
        val validConstructors = validConstructorsWithoutDefaults
                .map { checkSuppliedParamsOptional(it) }
                .zip(validConstructorsWithoutDefaults)
                .map{ Pair(it.first, it.second.second)}
                .filter{ it.first }

        // We may now have more than one completely valid constructor, just choose the first one.
        return validConstructors.first().second
    }

    private fun getParameterListWithOptionality(constructor: KFunction<T>): List<Pair<String, Boolean>> =
            constructor.parameters.map { Pair(it.name!!, it.isOptional) }

    private fun withLexicographicOrdering(strList: List<Pair<String, Boolean>>): List<Pair<String, Boolean>> = strList.sortedBy { it.first }

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

    private fun checkSuppliedParamsOptional(set: Pair<List<String>, KFunction<T>>): Boolean {
        val unsuppliedParams = set.first
        val constructor = set.second

        // N.B. Need to detect if params have DEFAULT values supplied, not just nulls as below.
        val areParamsOptional = unsuppliedParams.map {
            param -> constructor.parameters.find { cparam -> cparam.name == param }?.isOptional
        }.fold(true) {a,b -> a && (b?:true)}
        return areParamsOptional
    }

}
