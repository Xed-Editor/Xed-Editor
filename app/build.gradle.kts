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
    buildToolsVersion = "34.0.4"
    
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
            isCrunchPngs = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}else{
print("not a local environment skipping signing ")
buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isCrunchPngs = false
           
        }

        
    }




}
    
    
  
    
    
    
    defaultConfig {
        applicationId = "com.rk.xededitor"
        minSdk = 26
        targetSdk = 33
        versionCode = 11
        versionName = "1.1.1"
        
        
    }
    
    





    buildFeatures {
        viewBinding = true
        
    }
    
    
   compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlin {
        jvmToolchain(17)
    }
    
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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("io.github.Rosemoe.sora-editor:language-textmate")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation(platform("io.github.Rosemoe.sora-editor:bom:0.23.4"))
    implementation("io.github.Rosemoe.sora-editor:editor")
   // implementation("io.github.Rosemoe.sora-editor:editor-lsp")
  //  implementation("io.github.Rosemoe.sora-editor:language-java")
  //  implementation("io.github.Rosemoe.sora-editor:language-treesitter")
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.3")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.3")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
   // implementation ("com.github.termux:termux-shared:0.117")
    //implementation ("com.github.termux:terminal-view:0.117")
   /* implementation ("com.itsaky.androidide.treesitter:tree-sitter-java:4.1.0")
    implementation ("com.itsaky.androidide.treesitter:tree-sitter-kotlin:4.1.0")
    implementation ("com.itsaky.androidide.treesitter:tree-sitter-json:4.1.0")
    implementation ("com.itsaky.androidide.treesitter:tree-sitter-python:4.1.0")
    implementation ("com.itsaky.androidide.treesitter:tree-sitter-xml:4.1.0")*/
    
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.22.0")
   // implementation(fileTree("dir" to "libs", "includes" to listOf("*.jar")))
   
}
