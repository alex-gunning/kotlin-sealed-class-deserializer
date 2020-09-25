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
        val subs = type::sealedSubclasses.get()
        val subclassConstructorList = subs.map { it.constructors }
//        val requiredArgs = constructors
//                .map { c ->
//                    c.first()
//                            .parameters
//                            .map { Param(type = it.type.toString(), name = it.name!!, nullable = it.isOptional) }
//                            .sortedBy { it.name }
//                }

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
        val constructors = subclassConstructorList.map { it.first() }
        val orangeConstructor = findCorrectConstructor(constructors, args)
        val selectedConstructorParameters = subs.first().constructors.first().parameters

        // First find params which were not passed
        val missingArgs = selectedConstructorParameters
                .filterNot { cParam -> args.find { a -> a.first == cParam.name } != null }
                .map { Pair(it.index, null) }

        // then find params which were found
        val suppliedArgs = args
                .map { Pair(selectedConstructorParameters.find { p -> p.name == it.first }!!.index, it.second) }
                .sortedBy {
                    it.first
                }

        // merge them in the correct order
        val argsToBePassed = suppliedArgs
                .union(missingArgs)
                .sortedBy { it.first }
                .map { it.second }

        return orangeConstructor.call(*argsToBePassed.toTypedArray())
    }

    private fun findCorrectConstructor(
            constructorList: List<KFunction<T>>,
            parameters: List<Pair<String, Any>>): KFunction<T> {
       return constructorList.first()
    }
}
