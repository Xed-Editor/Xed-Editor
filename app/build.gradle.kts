import java.util.Properties

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.kotlinAndroid)
}

android {
  namespace = "com.rk.xededitor"
  compileSdk = 34
  
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
  
  lint {
    disable += "MissingTranslation"
  }
  
  
  val filePath = "/home/rohit/signing.properties"
  val file = File(filePath)
  
  if (file.exists()) {
    signingConfigs {
      create("release") {
        val propertiesFile = rootProject.file("/home/rohit/signing.properties")
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
        proguardFiles(
          getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
        )
        signingConfig = signingConfigs.getByName("release")
      }
    }
  } else {
    println("Not a local environment, skipping signing")
    buildTypes {
      getByName("release") {
        isMinifyEnabled = true
        isCrunchPngs = false
        proguardFiles(
          getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
        )
      }
    }
  }
  
  defaultConfig {
    applicationId = "com.rk.xededitor"
    minSdk = 26
    targetSdk = 34
    versionCode = 24
    versionName = "2.2.1"
  }
  
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    isCoreLibraryDesugaringEnabled = true
  }
  
  buildFeatures {
    viewBinding = true
    
  }
  kotlinOptions {
    jvmTarget = "17"
  }
}

dependencies {
  implementation(libs.appcompat)
  implementation(libs.material)
  implementation(libs.constraintlayout)
  implementation(libs.navigation.fragment)
  implementation(libs.navigation.ui)
  implementation(libs.asynclayoutinflater)
  implementation(libs.navigation.fragment.ktx)
  implementation(libs.navigation.ui.ktx)
  implementation(project(":xedPlugin"))
  implementation(libs.activity)
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
  implementation(platform("io.github.Rosemoe.sora-editor:bom:0.23.4"))
  //implementation("io.github.Rosemoe.sora-editor:editor")
  implementation("androidx.collection:collection:1.4.0")
  //  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.22.0")
  implementation("io.github.Rosemoe.sora-editor:language-textmate")
  
  
}
