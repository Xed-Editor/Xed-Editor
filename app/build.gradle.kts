import java.io.ByteArrayOutputStream
import java.util.Properties

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.kotlinAndroid)
  alias(libs.plugins.compose.compiler)
}




fun getGitCommitHash(): String {
  val stdout = ByteArrayOutputStream()
  exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    standardOutput = stdout
  }
  return stdout.toString().trim()
}


android {
  namespace = "com.rk.xededitor"
  compileSdk = 34


  println("Building for commit ${getGitCommitHash()}")


  //todo remove this before release
  lintOptions {
    disable("MissingTranslation")
  }
  
  
  
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
  
  signingConfigs {
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
        keyAlias = properties["keyAlias"] as String?
        keyPassword = properties["keyPassword"] as String?
        storeFile = if (isGITHUB_ACTION) {
          File("/tmp/xed.keystore")
        } else {
          File(properties["storeFile"] as String?)
        }
        
        storePassword = properties["storePassword"] as String?
      } else {
        println("Signing properties file not found at $propertiesFilePath")
      }
    }
  }
  
  
  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isCrunchPngs = false
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
      )
      signingConfig = signingConfigs.getByName("release")
    }
    getByName("debug") {
      storeFile = file(layout.buildDirectory.dir("../testkey.keystore"))
      storePassword = "testkey"
      keyAlias = "testkey"
      keyPassword = "testkey"
    }
  }
  
  defaultConfig {
    applicationId = "com.rk.xededitor"
    minSdk = 26
    //noinspection ExpiredTargetSdkVersion
    targetSdk = 28
    versionCode = 32
    versionName = "2.7.2"
    vectorDrawables {
      useSupportLibrary = true
    }
  }
  
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    isCoreLibraryDesugaringEnabled = true
  }
  
  buildFeatures {
    viewBinding = true
    compose = true
  }
  
  kotlinOptions {
    jvmTarget = "17"
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.1"
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
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
  implementation(project(":editor"))
  implementation(project(":language-textmate"))
  implementation(libs.lifecycle.livedata.ktx)
  implementation(libs.lifecycle.viewmodel.ktx)
  implementation(libs.lifecycle.runtime.ktx)
  implementation(libs.activity.compose)
  implementation(platform(libs.compose.bom))
  implementation(libs.ui)
  implementation(libs.ui.graphics)
  implementation(libs.ui.tooling.preview)
  implementation(libs.material3)
  implementation(libs.navigation.compose)
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(libs.terminal.view)
  implementation(libs.terminal.emulator)
  implementation(libs.utilcode)
  implementation(project(":libsettings"))
  implementation(project(":libPlugin"))
  implementation(project(":libRunner"))
  implementation(project(":commons"))
  implementation(project(":FileTree"))
  implementation(libs.sshj)
  implementation(libs.commons.net)
  implementation(libs.gson)
  implementation(libs.jcodings)
  implementation(libs.joni)
  implementation(libs.snakeyaml.engine)
  implementation(libs.jdt.annotation)
  implementation(libs.ktsh)
  implementation(libs.swiperefreshlayout)
  implementation(libs.okhttp)
  implementation(libs.org.eclipse.jgit)
  implementation(libs.coil.compose)
}
