plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "com.rk.extension"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
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
    implementation(project(":core:components"))
    implementation(project(":core:resources"))

    // Editor modules for Markdown rendering
    implementation(project(":editor"))
    implementation(project(":editor-lsp"))

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.semver)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.gson)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    testImplementation(libs.junit)
    testImplementation(libs.tests.google.truth)
}
