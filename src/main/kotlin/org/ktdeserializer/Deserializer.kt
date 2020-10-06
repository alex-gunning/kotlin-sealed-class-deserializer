package org.ktdeserializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import kotlin.reflect.*
import kotlin.reflect.full.createType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

class Deserializer<T : Any>(private val type: KClass<T>) : JsonDeserializer<T>() {
    override fun deserialize(parser: JsonParser, ctx: DeserializationContext): T {
        val sealedSubclasses = type::sealedSubclasses.get()
        val constructors = sealedSubclasses.map { it.constructors }.map { it.first() }

        val mapper = parser.codec as ObjectMapper
        val node: JsonNode = mapper.readTree(parser)

        // Create arguments list for incoming deserialized fields
        val args: List<Pair<String, Any>> = createJSONArgTree(node)

        // Convert supplied arg values to their types for comparison
        val argTypes = args
                .map { coerceToTypes(it) }

        // Find out which constructor the supplied args match
        val correctConstructor = findCorrectConstructor(constructors, argTypes)

        // then create map of supplied params.
        //      Create an object on a class with
        //      (someKType.classifier as KClass<*>).createInstance()
        val argsToBePassed = args
                .map { Pair(correctConstructor.parameters.find { p -> p.name == it.first }, it.second) }
                .fold(mapOf<KParameter, Any?>()) { map, param -> map + Pair(param.first!!, param.second) }

        // and pass to the constructor as named arguments. Kotlin will handle defaults
        return correctConstructor.callBy(argsToBePassed)
    }

    private fun coerceToTypes(arg: Pair<String, Any>): Pair<String, Any> =
            when (arg.second) {
                is List<*> -> Pair(arg.first, (arg.second as List<*>).map { coerceToTypes(it as Pair<String, Any>) })
                else -> Pair(arg.first, arg.second::class.createType())
            }

    private fun createJSONArgTree(node: JsonNode): List<Pair<String, Any>> =
            node.fields().asSequence().toHashSet().map {
                when (it.value.nodeType) {
                    JsonNodeType.NUMBER -> Pair(it.key, it.value.intValue())
                    JsonNodeType.STRING -> Pair(it.key, it.value.textValue())
                    JsonNodeType.BOOLEAN -> Pair(it.key, it.value.booleanValue())
                    JsonNodeType.OBJECT -> Pair(it.key, createJSONArgTree(it.value))
                    JsonNodeType.ARRAY -> TODO("Iterative step for array")
                    else -> throw Error("Deserialized data type not yet supported.")
                }
            }.toList()


    private fun findCorrectConstructor(
            constructorList: List<KFunction<T>>,
            suppliedParamList: List<Pair<String, Any>>): KFunction<T> {
        val constructorParamList = constructorList
                .map { getParameterListWithType(it) }
                .map { constructorArgTree(it) }


        /*  A boolean determining whether the supplied parameters are the a subtree
         *  of each constructor.
         */
        val constructorFitsParameters = constructorParamList.map { includesSubtree(suppliedParamList, it) }

        /*  Constructors which the supplied params COULD fit in to
         *  (but are not necessarily valid)
         */
        val fittingConstructors = constructorParamList
                .zip(constructorList)
                .zip(constructorFitsParameters)
                .filter { it.second }
                .map { it.first }

        /*  Remove supplied params from each constructor in the list if the name matches.
        *   We checked that the parameter name and type matches in the subtree, so we can just remove
        *   the params by name here, which is much easier.
        *   After we are done, flatten these params to one-level deep. Duplicates are tolerated as they came from
        *   different levels.
        */
        val constructorsMinusSuppliedParams: List<List<Pair<String, KType>>> =
                fittingConstructors
                        .map { removeSubtree(suppliedParamList, it.first) }
                        .map { removeBlanks(it) }
                        .map { flattenConstructor(it) }

        //  Check that any remainder fields are either nullable OR have a default.
        val allFieldsOptional = constructorsMinusSuppliedParams
                .map { checkAllFieldsOptional(it) }
                .zip(fittingConstructors.map { it.second })
        TODO()
        /*  Associate the actual constructor with the still-unsupplied params
        *   and remove any null (invalid) constructors.
        */
//        val validConstructorsWithoutDefaults = constructorsMinusSuppliedParams
//                .zip(constructorList)
//                .mapNotNull { if (it.first == null) null else it }
//                .map { Pair(it.first!!, it.second) }

        /*  Create boolean indicating whether remaining params are optional.
        *   Associate with the constructor and filter the valid ones by the boolean.
        *   Return just the valid constructors.
        */
//        val validConstructors = validConstructorsWithoutDefaults
//                .map { checkTheseParamsOptional(it) }
//                .zip(validConstructorsWithoutDefaults)
//                .map { Pair(it.first, it.second.second) }
//                .filter { it.first }
//                .map { it.second }

        // We may now have more than one completely valid constructor, just choose the first one as a tiebreaker.
//        return validConstructorsWithoutDefaults.first().second
    }

    // Returns true if all needles exist in haystack.
    // This means true if supplied params match the constructor params but not necessarily all there.
    private fun includesSubtree(needles: List<Pair<String, Any>>, haystack: List<Pair<String, Any>>): Boolean {
        val a = needles.map { needl ->
            val matcher = haystack.find { hay -> hay.first == needl.first }
            if (matcher == null) {
                false
            } else if (needl.second is KType && matcher.second is KType) {
                (needl.second as KType).jvmErasure.primaryConstructor == (matcher.second as KType).jvmErasure.primaryConstructor
            } else if (needl.second is List<*> && matcher.second is List<*>) {
                includesSubtree(needl.second as List<Pair<String, Any>>, matcher.second as List<Pair<String, Any>>)
            } else {
                false
            }
        }
        return a.fold(true) { a, b -> a && b }
    }

    private fun getParameterListWithType(constructor: KFunction<T>): List<Pair<String, KType>> =
            constructor.parameters.map { Pair(it.name!!, it.type) }

    private fun createParameterArgTree(parameter: Pair<String, KType>): Pair<String, Any> = when (parameter.second.classifier) {
        // TODO: for this to be really robust we would need to check every single constructor, not just the first one.
        Int::class -> parameter
        String::class -> parameter
        Boolean::class -> parameter
        Float::class -> parameter
        else -> Pair(
                parameter.first,
                parameter.second.jvmErasure.constructors.first().parameters
                        .map {
                            createParameterArgTree(Pair(it.name!!, it.type))
                        }
        )
    }

    private fun constructorArgTree(constructorArgs: List<Pair<String, KType>>): List<Pair<String, Any>> =
            constructorArgs.map { createParameterArgTree(it) }

    private fun paramNamesWithTypes(params: List<Triple<String, KType, Boolean>>): List<Pair<String, KType>> =
            params.map { Pair(it.first, it.second) }

    private fun removeIfExists(haystack: List<Pair<String, Any>>, needle: String): List<Pair<String, Any>>? {
        return if (haystack.find { it.first == needle } != null) {
            haystack.filterNot { it.first == needle }
        } else {
            null
        }
    }

    // Returns null if a needle does not exist.
    private fun removeAll(haystack: List<Pair<String, Any>>?, needles: List<Pair<String, Any>>): List<Pair<String, Any>>? {
        if (haystack == null) {
            return null
        }
        if (needles.isEmpty()) {
            return haystack
        }
        val newHaystack = removeIfExists(haystack, needles.first().first)
        return removeAll(newHaystack, needles.slice(1..needles.lastIndex))
    }

    private fun removeSubtree(needles: List<Pair<String, Any>>, haystack: List<Pair<String, Any>>): List<Pair<String, Any?>> {
        val a = haystack.map { hay ->
            val needl = needles.find { it.first == hay.first }
            if (needl == null) {
                hay
            } else {
                when (needl!!.second) {
                    is KType -> null
                    is List<*> -> Pair(
                            needl.first,
                            removeSubtree(needl.second as List<Pair<String, Any>>, hay.second as List<Pair<String, Any>>)
                    )
                    else -> throw Error("WHAAAAAT?")
                }
            }
        }
        return a.filterNotNull()
    }

    private fun removeBlanks(haystack: List<Pair<String, Any?>>): List<Pair<String, Any?>> {
        val a = haystack.map { hay ->
            if (hay.second is List<*> && (hay.second as List<*>).isEmpty()) {
                null
            } else {
                when (hay.second) {
                    is KType -> hay
                    is List<*> -> removeBlanks(hay.second as List<Pair<String, Any?>>)
                    else -> throw Error("NOOOO")
                }
            }
        }
        return a.filterNotNull() as List<Pair<String, Any?>>
    }

    private fun checkAllFieldsOptional(params: List<Pair<String, KType>>): Boolean {
        val a = params.map {
            it.second.isMarkedNullable
        }
        return a.fold(true) { c, b -> c && b }
    }

    private fun flattenConstructor(params: List<Any>): List<Pair<String, KType>> {
        val a = params.map { p ->
            when (p) {
                is Pair<*, *> -> p
                is List<*> -> flattenConstructor(p as List<Any>)
                else -> throw Error("")
            }
        }
        val b = rollupOneLevel(a)
        return b as List<Pair<String, KType>>
    }

    private fun rollupOneLevel(list: List<Any>): List<Pair<String, KType>> {
        val a = list.fold(listOf<Pair<String, KType>>()) { acc, p ->
            when (p) {
                is Pair<*, *> -> acc + p as Pair<String, KType>
                is List<*> -> acc.plus(p as List<Pair<String, KType>>)
                else -> throw Error("")
            }
        }
        return a
    }

    private fun checkTheseParamsOptional(paramPair: Pair<List<String>, KFunction<T>>): Boolean {
        val unsuppliedParams = paramPair.first
        val constructor = paramPair.second

        val areParamsOptional = unsuppliedParams.map { param ->
            constructor.parameters.find { cparam -> cparam.name == param }?.isOptional
        }.fold(true) { a, b -> a && (b ?: true) }
        return areParamsOptional
    }

}
