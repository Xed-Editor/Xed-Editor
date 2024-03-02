import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
}

val propertiesFile = rootProject.file("signing.properties")
val properties = Properties()
properties.load(propertiesFile.inputStream())


android {
    namespace = "com.rk.xededitor"
    compileSdk = 33
    
    
    signingConfigs {
        create("release") {
            keyAlias = properties["keyAlias"] as String
            keyPassword = properties["keyPassword"] as String
            storeFile = file(properties["storeFile"] as String)
            storePassword = properties["storePassword"] as String
        }
    }
    
    defaultConfig {
        applicationId = "com.rk.xededitor"
        minSdk = 25
        targetSdk = 33
        versionCode = 1
        versionName = "1.0.1"
        
        vectorDrawables { 
            useSupportLibrary = true
        }
    }
    
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        viewBinding = true
        
    }
    
}

/*tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}*/



dependencies {
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation(platform("io.github.Rosemoe.sora-editor:bom:0.23.3"))
    implementation("io.github.Rosemoe.sora-editor:editor")
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.3")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.5.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.3")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}