package org.ktdeserializer

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Test
import org.junit.Before
import org.junit.jupiter.api.DisplayName

class DeserializerTest {
    sealed class Fruits {
        data class Orange(val colour: String, val bitterness: Int? = null, val dimpled: Boolean) : Fruits()
        data class Apple(val colour: String) : Fruits()
    }
    private val objectMapper = jacksonObjectMapper()
    private val sealedClassDeserializer: SimpleModule = SimpleModule().addDeserializer(Fruits::class.java, Deserializer(Fruits::class))

    @Before
    fun setup() {
        objectMapper.registerModule(sealedClassDeserializer)
    }

    @Test
    @DisplayName("All params passed should retrieve an orange")
    fun testSerialisation() {
        val json = """{
                    |"colour":"yellowish",
                    |"bitterness":31,
                    |"dimpled":true
                    |}""".trimMargin()

        val fruitType = when (objectMapper.readValue<Fruits>(json)) {
            is Fruits.Orange -> "is an orange"
            is Fruits.Apple -> "is an apple"
        }
        assert(fruitType.equals("is an orange"))
    }

    @Test
    @DisplayName("Passing only required values should retrieve the correct subclass by supplying nulls")
    fun chooseCorrectSubclass() {
        val json = """{
                    |"colour":"yellowish",
                    |"dimpled":true
                    |}""".trimMargin()

        val fruitType = when (objectMapper.readValue<Fruits>(json)) {
            is Fruits.Orange -> "is an orange"
            is Fruits.Apple -> "is an apple"
        }
        assert(fruitType.equals("is an orange"))
    }

    @Test
    @DisplayName("Selects correct subclass even if supplied parameter names match")
    fun chooseCorrectSubclassEvenIfParamNamesClash() {
        val json = """{
                    |"colour":"pink"
                    |}""".trimMargin()

        val fruit = objectMapper.readValue<Fruits>(json)
        val fruitType = when (fruit) {
            is Fruits.Orange -> throw Error()
            is Fruits.Apple -> "is an apple"
        }

        assert(fruit.colour == "pink")
        assert(fruitType.equals("is an apple"))
    }
}

