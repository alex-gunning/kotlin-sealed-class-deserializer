package org.ktdeserializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.JsonNodeType

interface Serializable

class Deserializer<T: Serializable> : JsonDeserializer<T>() {

    data class Param(val type: String, val name: String, val nullable: Boolean, val value: Any? = null)

    fun Param.matches(other: Param): Boolean =
        this.type == other.type && this.name == other.name && this.nullable == other.nullable

    override fun deserialize(parser: JsonParser, ctx: DeserializationContext): T {
        val b:Class<T>? = null
        val constructors = b!!::class.sealedSubclasses.map { it.constructors }
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
                JsonNodeType.STRING -> TODO("")//Param(type = "kotlin.String", name = it.key, value = it.value.textValue())
                JsonNodeType.NUMBER -> TODO("")//Param(type = "kotlin.Int", name = it.key, value = it.value.intValue())
                JsonNodeType.BOOLEAN -> TODO("Implement boolean")
                JsonNodeType.OBJECT -> TODO("Recursive step for this object")
                else -> throw Error("Deserialized data type not yet supported.")
            }
        }.toList()

        // Match args to requiredArgs while considering nullable fields
        // requiredArgs.map { constructor -> constructor.m}
        // On match found, rearrange args as per original parameter order

        val orangeConstructor = constructors.first()
        // val obj = orangeConstructor.first().call(*args)

        val msgNode = node.get("a")
//        if (msgNode.nodeType == JsonNodeType.STRING) {
//            return R.orange(msgNode.textValue())
//        } else {
//            return R.apple(node.get("b").intValue(), node.get("c").textValue(), node.get("aa").booleanValue())
//        }
        val thething: Class<T> = orangeConstructor.first().call(*args.toTypedArray())
        return thething.constructors.first().newInstance(*args.toTypedArray()) as T
    }
}
