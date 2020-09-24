plugins {
    kotlin("jvm") version "1.4.10"
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.11.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation("junit:junit:4.12")
}