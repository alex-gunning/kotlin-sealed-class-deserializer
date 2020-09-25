package org.ktdeserializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import kotlin.reflect.KClass

class Deserializer<T: Any>(private val type: KClass<T>) : JsonDeserializer<T>() {
    data class Param(val type: String, val name: String, val nullable: Boolean? = null, val value: Any? = null)

    fun Param.matches(other: Param): Boolean =
        this.type == other.type && this.name == other.name && this.nullable == other.nullable

    override fun deserialize(parser: JsonParser, ctx: DeserializationContext): T {
        val subs = type::sealedSubclasses.get()
        val constructors = subs.map { it.constructors }
        val requiredArgs = constructors
            .map { c ->
                c.first()
                    .parameters
                    .map { Param(type = it.type.toString(), name = it.name!!, nullable = it.isOptional) }
                    .sortedBy { it.name }
            }

        val mapper = parser.codec as ObjectMapper
        val node: JsonNode = mapper.readTree(parser)

        // Incoming deserialized fields - Create args list
        val args = node.fields().asSequence().toHashSet().map {
            when (it.value.nodeType) {
                JsonNodeType.NUMBER -> Param(type = "kotlin.Int", name = it.key, value = it.value.intValue())
                JsonNodeType.STRING -> Param(type = "kotlin.String", name = it.key, value = it.value.textValue())
                JsonNodeType.BOOLEAN -> TODO("Implement boolean")
                JsonNodeType.OBJECT -> TODO("Recursive step for this object")
                else -> throw Error("Deserialized data type not yet supported.")
            }
        }.toList()

        val orangeConstructor = constructors.first() // DORK - Find correct constructor
        val selectedConstructorParameters = subs.first().constructors.first().parameters


        // First find params which were not passed
        val missingArgs = selectedConstructorParameters
                //.filter { it.isOptional }
                .filterNot { cParam -> args.find { a -> a.name == cParam.name } != null }
                .map { Pair(it.index, null) }

        // then find params which were found
        val suppliedArgs = args
                .map { Pair(selectedConstructorParameters.find { p -> p.name == it.name }!!.index, it.value) }
                .sortedBy {
                    it.first
                }

        val argsToBePassed = suppliedArgs
                .union(missingArgs)
                .sortedBy { it.first }
                .map { it.second }

        return orangeConstructor.first().call(*argsToBePassed.toTypedArray())
    }
}
