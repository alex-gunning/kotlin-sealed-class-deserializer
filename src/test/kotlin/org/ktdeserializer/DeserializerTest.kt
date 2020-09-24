package org.ktdeserializer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.jupiter.api.DisplayName

sealed class Bleh(): Serializable {
    data class Orange(val name: String): Bleh()
    data class Apple(val age: Int): Bleh()
}

class DeserializerTest {
    @Test
    fun testMyLanguage() {
        assertEquals(1, 1)
    }

    @Test
    @DisplayName("Jackson Serialisation")
    fun testSerialisation() {
        val objectMapper = jacksonObjectMapper()

        val module: SimpleModule = SimpleModule().addDeserializer(Bleh::class.java, Deserializer())
        objectMapper.registerModule(module)
        val json = """{"name":"Myname"}"""
        val bleh = objectMapper.readValue<Bleh>(json)
        val returned = when (bleh) {
            is Bleh.Orange -> "is an orange"
            is Bleh.Apple -> "is an apple"
        }
        assert(returned.equals("is an orange"))
    }
}

