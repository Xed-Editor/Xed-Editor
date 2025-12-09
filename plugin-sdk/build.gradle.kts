import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.0.10"
    id("com.gradleup.shadow") version "9.3.0"
}

group = "com.rk"
version = "1.0"

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("sdk.jar")
    isZip64 = true
    destinationDirectory.set(file("./output"))
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
