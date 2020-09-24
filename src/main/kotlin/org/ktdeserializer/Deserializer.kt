package org.ktdeserializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.JsonNodeType
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.typeOf

interface Serializable

class Deserializer<T: Any>(val type: Class<T>) : JsonDeserializer<T>() {
    data class Param(val type: String, val name: String, val nullable: Boolean? = null, val value: Any? = null)

    fun Param.matches(other: Param): Boolean =
        this.type == other.type && this.name == other.name && this.nullable == other.nullable

    override fun deserialize(parser: JsonParser, ctx: DeserializationContext): T {
        val a = type.classes
        val constructors = type.classes.map { it.constructors }
        val requiredArgs = constructors
            .map { c ->
                c.first()
                    .parameters
                    .map { Param(type = it.type.toString(), name = it.name!!, nullable = null) } // Need to account for nullables here.
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
        // On match found, rearrange args as per original parameter order

        val orangeConstructor = constructors.first()

        // Currently crashes here
        return orangeConstructor.first().newInstance(*args.toTypedArray()) as T
    }
}
