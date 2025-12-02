plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "org.robok.engine.core.components"
    compileSdk = 36

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
    implementation(platform(libs.compose.bom))
    implementation(libs.material3)
    implementation(libs.material)
    implementation(libs.appcompat)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.activity.compose)
    implementation(libs.androidx.material.icons.core)
    // implementation("androidx.compose.material:material-icons-extended:1.7.8")
}
