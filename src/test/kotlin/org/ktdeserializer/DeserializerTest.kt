package org.ktdeserializer

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Test
import org.junit.Before
import org.junit.Ignore
import org.junit.jupiter.api.DisplayName

class DeserializerTest {
    sealed class Fruits {
        data class Orange(val colour: String, val bitterness: Int? = null, val dimpled: Boolean) : Fruits()
        data class Apple(val colour: String, val sweetness: Int? = 3) : Fruits()
    }
    sealed class Shapes {
        data class Triangle(val numSides: Int, val pointyOuch: Boolean): Shapes()
        data class Circle(val diameter: Float, val pointyOuch: Boolean): Shapes()
        data class DumbStatue(val pointyOuch: Boolean, val triangleBase: Triangle, val circleUpper: Circle): Shapes()
    }

    private val objectMapper = jacksonObjectMapper()
    private val fruitsSealedClassDeserializer: SimpleModule = SimpleModule().addDeserializer(Fruits::class.java, Deserializer(Fruits::class))
    private val shapesSealedClassDeserializer: SimpleModule = SimpleModule().addDeserializer(Shapes::class.java, Deserializer(Shapes::class))

    @Before
    fun setup() {
        objectMapper.registerModule(fruitsSealedClassDeserializer)
        objectMapper.registerModule(shapesSealedClassDeserializer)
    }

    @Test
    @DisplayName("All params passed should retrieve an orange")
    fun allParmsSupplied() {
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
    @DisplayName("""Passing only required values should retrieve the correct subclass by supplying nulls
                where optionals are not supplied.""")
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
    @DisplayName("Selects correct subclass even if parameter names match more than one subclass.")
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
        assert(fruit.sweetness == 3)
        assert(fruitType.equals("is an apple"))
    }

    @Test
    @Ignore
    @DisplayName("Can deserialise compound objects")
    fun canDeserialiseCompoundObject() {
        val json = """{
                    |"pointyOuch":false,
                    |"triangleBase": {
                    |"numSides":3,
                    |"pointyOuch":true
                    |},
                    |"circleUpper": {
                    |"diameter":5,
                    |"pointyOuch":false
                    |}}""".trimMargin()

        val shape = objectMapper.readValue<Shapes>(json)

        val shapeType = when(shape) {
            is Shapes.Circle -> throw Error("Circle is not the correct shape")
            is Shapes.Triangle -> throw Error("Triangle is not the correct shape")
            is Shapes.DumbStatue -> "a beautiful statue"
        }

        assert(shapeType.equals("a beautiful statue"))
    }
}

