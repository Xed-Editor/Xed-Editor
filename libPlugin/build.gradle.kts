plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.rk.libplugin"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    val kotlin = "2.0.0"
    implementation(libs.core.ktx)
    implementation("org.jetbrains.kotlin:kotlin-scripting-common:$kotlin")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlin")
    implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies:$kotlin")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:$kotlin")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlin")
}