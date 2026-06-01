plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "com.rk.ai.streaming"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }
}

dependencies {
    api(project(":core:vibe-coding:ai-core"))

    api(libs.okhttp)
    api(libs.okhttp.sse)
    api(libs.okhttp.logging)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines)
    api(libs.gson)
}
