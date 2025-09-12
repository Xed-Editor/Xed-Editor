plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.robok.engine.core.components"
    compileSdk = 35
    
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }
   
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.appcompat)
    
    implementation(platform(libs.compose.bom))
    implementation(libs.material3)
    implementation(libs.material)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.activity.compose)
}