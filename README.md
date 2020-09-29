## Kotlin Sealed-class Deserialization helper
> NB. Gradle cannot run on Java 11 (Requires Java 8)

#### What is it?

A general deserializer plugin for Kotlin and Jackson.
Allows for more idiomatic Kotlin to be written without
the need to write a specialised serializer each time.
Just plug in the sealed class and off you go.

##### Example

Create a sealed class

```
sealed class Vehicles {
    data class Ferrari(val colour: String, val price: Int) : Vehicles()
    data class Golfcart(val colour: String?) : Vehicles()
    data class Motorcycle(val colour: String, val topSpeed: Int, val sideCarName: String?): Vehicles()
}
```

and register as a custom deserializer

```
val objectMapper = jacksonObjectMapper()
val vehicleSealedClassDeserializer: SimpleModule = 
    SimpleModule().addDeserializer(
        Vehicles::class.java,
        Deserializer(Vehicles::class)
    )
objectMapper.registerModule(vehicleSealedClassDeserializer)
```
and use as required (without building a custom deserializer each time).
```
val json = """{
              |"colour": "grey",
              |"price": 300000  
              |}""".trimMargin()

val myVehicle: Vehicle = objectMapper.readValue(json)

when (myVehicle) {
    is Ferrari -> println("I should buy a Tesla")
    is Golfcart -> throw Error("I don't like driving to work like this")
    is Motorcycle -> println("This is gooooood!")
}

```

#### Still to do
1. ~~Handle default arguments that are other than nulls.~~
2. ~~Select correct subclass from sealed class.~~
3. Handle objects recursively.
4. Handle arrays using some sort of iteration.
5. Handle object serialisation methods.