import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "9.4.1"
}

group = "com.rk"
version = "1.0"

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("sdk.jar")
    isZip64 = true
    destinationDirectory.set(file("./output"))
    
    val mappingFile = file("../app/build/outputs/mapping/release/mapping.txt")
    if (mappingFile.exists()) {
        from(mappingFile)
    } else {
        logger.warn("R8 mapping.txt not found at: ${mappingFile.absolutePath}. The sdk.jar will not include mapping.txt.")
    }
}


repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
