plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "com.rk.ai"
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
    implementation(project(":core:main"))


    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.kotlin.reflect)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.nanohttpd)
    implementation(libs.utilcode)
    implementation(libs.jgit)
    implementation(libs.commons.net)
    implementation(libs.androidx.documentfile)
    implementation(libs.lsp4j)

    implementation(project(":editor"))
    implementation(project(":editor-lsp"))
    implementation(project(":language-textmate"))
    implementation(project(":core:terminal-view"))
    implementation(project(":core:terminal-emulator"))
    implementation(project(":core:resources"))
    implementation(project(":core:extension"))
    implementation(project(":core:vibe-coding:ai-service"))
}
