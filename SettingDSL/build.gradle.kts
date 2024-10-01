import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinParcelize)
}

// Versions
val libraryVersion = "2.4.0-beta2"
val libraryGroup = "de.maxr1998"
val libraryName = "modernandroidpreferences"
val prettyLibraryName = "ModernAndroidPreferences"



android {
    namespace = "de.Maxr1998.modernpreferences"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            lint {
                disable.add("MissingTranslation")
                disable.add("ExtraTranslation")
            }
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
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.navigation.ui.ktx)

    implementation(libs.activity)
}