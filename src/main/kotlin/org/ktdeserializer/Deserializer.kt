package org.ktdeserializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.JsonNodeType
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance
import kotlin.reflect.typeOf

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

        // Match args to requiredArgs while considering nullable fields
        // requiredArgs.map { constructor -> constructor.m}
        val orangeConstructor = constructors.first() // Change to iterate all subtypes
        val parameterOrder = subs.first().constructors.first().parameters

        // On match found, rearrange args as per original parameter order
        val realArgs = args
                .map { it }
                .sortedBy {
                    // Match params from JSON to arg order in constructor
                    parameterOrder.find { p -> p.name == it.name }!!.index
                }
                .map { it.value }

        return orangeConstructor.first().call(*realArgs.toTypedArray())
    }
}
