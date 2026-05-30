plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "com.rk.ai.mcp"
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
    api(project(":core:ai-core"))
    api(project(":core:ai-streaming"))
    api(project(":core:ai-models"))
    api(project(":core:ai-persistence"))


    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines)
    api(libs.okhttp)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.modelcontextprotocol.kotlin.sdk)
}
