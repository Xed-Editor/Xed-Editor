plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "com.rk.ai.providers"
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
    api(project(":core:ai-core"))
    api(project(":core:ai-streaming"))
    api(project(":core:ai-models"))

    api(libs.okhttp)
    api(libs.okhttp.sse)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines)

    implementation(libs.androidx.compose.ui)
    implementation(platform(libs.androidx.compose.bom))
}
