plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "com.rk.ai.agent"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }
}

dependencies {
    api(project(":core:vibe-coding:ai-core"))
    api(project(":core:vibe-coding:ai-streaming"))
    api(project(":core:vibe-coding:ai-models"))
    api(project(":core:vibe-coding:ai-providers"))
    api(project(":core:vibe-coding:ai-mcp-client"))
    api(project(":core:vibe-coding:agent-tools-search"))
    api(project(":core:vibe-coding:ai-persistence"))
    api(project(":core:vibe-coding:ai-service"))

    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines)
    api(libs.okhttp)

    implementation(libs.androidx.compose.ui)
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.pebble)
}
