import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
}



android {
    namespace = "com.rk.xededitor"
    compileSdk = 33

    
  //required by izzyOnDroid repo 
   dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    
    
val filePath = "/sdcard/AndroidIDEProjects/signing.properties"
val file = File(filePath)

if (file.exists()) {
    println("File exists.")
    
    signingConfigs {
        create("release") {
            val propertiesFile = rootProject.file("/sdcard/AndroidIDEProjects/signing.properties")
            val properties = Properties()
            properties.load(propertiesFile.inputStream())
            keyAlias = properties["keyAlias"] as String
            keyPassword = properties["keyPassword"] as String
            storeFile = file(properties["storeFile"] as String)
            storePassword = properties["storePassword"] as String
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            //proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
}else{
print("not a local environment skipping signing ")
buildTypes {
        getByName("release") {
            isMinifyEnabled = true
           // proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        
    }




}
    
    
  
    
    
    
    defaultConfig {
        applicationId = "com.rk.xededitor"
        minSdk = 26
        targetSdk = 33
        versionCode = 7
        versionName = "1.0.7"
        
        
    }
    
    





    buildFeatures {
        viewBinding = true
        
    }
    
    
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {
        val lifecycle_version = "2.5.1"
        implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")
        implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle_version")
        implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:$lifecycle_version")
        implementation("androidx.lifecycle:lifecycle-common-java8:$lifecycle_version")
        implementation("androidx.lifecycle:lifecycle-service:$lifecycle_version")
        implementation("androidx.lifecycle:lifecycle-process:$lifecycle_version")
        implementation("androidx.lifecycle:lifecycle-reactivestreams-ktx:$lifecycle_version")
  // implementation ("com.squareup.leakcanary:leakcanary-android:3.0-alpha-1")
    //coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:$version")
   // implementation("io.github.Rosemoe.sora-editor:language-textmate")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation(platform("io.github.Rosemoe.sora-editor:bom:0.23.3"))
    implementation("io.github.Rosemoe.sora-editor:editor")
    implementation("io.github.Rosemoe.sora-editor:editor-lsp")
    implementation("io.github.Rosemoe.sora-editor:language-java")
    implementation("io.github.Rosemoe.sora-editor:language-treesitter")
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.3")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.3")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
