
plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.rk.xededitor"
    compileSdk = 33
    
    defaultConfig {
        applicationId = "com.rk.xededitor"
        minSdk = 28
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        
        vectorDrawables { 
            useSupportLibrary = true
        }
    }
    
    

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        
    }
    
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {

    implementation(platform("io.github.Rosemoe.sora-editor:bom:0.23.3"))
    implementation("io.github.Rosemoe.sora-editor:editor")
  //  implementation("io.github.Rosemoe.sora-editor:language-textmate")
    
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.3")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.5.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.3")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
