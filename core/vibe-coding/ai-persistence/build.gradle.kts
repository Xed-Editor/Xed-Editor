plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "com.rk.ai.persistence"
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
    api(project(":core:vibe-coding:ai-models"))
    api(project(":core:vibe-coding:ai-streaming"))
    api(project(":core:vibe-coding:ai-providers"))
    api(project(":core:vibe-coding:agent-tools-search"))

    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines)
    api(libs.okhttp)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.room.paging)
}
