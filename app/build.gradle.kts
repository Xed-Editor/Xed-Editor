import java.util.Properties

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.kotlinAndroid)
}

android {
  namespace = "com.rk.xededitor"
  compileSdk = 34

  lintOptions {
    disable("MissingTranslation")
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  /*signingConfigs {
    create("release") {


      val isGITHUB_ACTION = System.getenv("GITHUB_ACTIONS") == "true"

      val propertiesFilePath = if (isGITHUB_ACTION) {
        "/tmp/signing.properties"
      } else {
        "/home/rohit/signing.properties"
      }

      val propertiesFile = File(propertiesFilePath)
      if (propertiesFile.exists()) {
        val properties = Properties()
        properties.load(propertiesFile.inputStream())
        keyAlias = properties["keyAlias"] as String
        keyPassword = properties["keyPassword"] as String
        if (isGITHUB_ACTION){
          storeFile = File("/tmp/xed.keystore")
        }else{
          storeFile = File(properties["storeFile"] as String)
        }

        storePassword = properties["storePassword"] as String
      } else {
        println("Signing properties file not found at $propertiesFilePath")
      }
    }
  }*/

  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      isCrunchPngs = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
      )
      signingConfig = signingConfigs.getByName("release")
    }
  }

  defaultConfig {
    applicationId = "com.rk.xededitor"
    minSdk = 26
    targetSdk = 34
    versionCode = 29
    versionName = "2.5.0"
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

  implementation(libs.activity)
//  implementation(project(":filetree"))
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
  implementation(platform("io.github.Rosemoe.sora-editor:bom:0.23.4"))
  //implementation("io.github.Rosemoe.sora-editor:editor")
  implementation("androidx.collection:collection:1.4.2")
  //  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.22.0")
  //implementation("io.github.Rosemoe.sora-editor:language-textmate")
  implementation("org.apache.commons:commons-vfs2:2.9.0")
  implementation("com.github.mwiede:jsch:0.2.8")

  implementation(libs.terminal.view)
  //implementation(libs.terminal.shared)
  implementation(libs.terminal.emulator)
  implementation(libs.utilcode)
 //implementation(libs.modernandroidpreferences)
  implementation(project(":libsettings"))

  implementation(libs.sshj)
  implementation(libs.commons.net)

  implementation(libs.gson)
  implementation(libs.jcodings)
  implementation(libs.joni)

  implementation(libs.snakeyaml.engine)
  implementation(libs.jdt.annotation)

  implementation(libs.nb.javac.android)
 // implementation(libs.zyron.filetree)
}
