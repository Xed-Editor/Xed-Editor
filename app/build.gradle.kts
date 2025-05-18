import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
}


android {
    namespace = "com.rk.application"
    compileSdk = 35

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
                "/home/rohit/Android/xed-signing/signing.properties"
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
                    (properties["storeFile"] as String?)?.let { File(it) }
                }

                storePassword = properties["storePassword"] as String?
            } else {
                println("Signing properties file not found at $propertiesFilePath")
            }
        }
        getByName("debug") {
            storeFile = file(layout.buildDirectory.dir("../testkey.keystore"))
            storePassword = "testkey"
            keyAlias = "testkey"
            keyPassword = "testkey"
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            isCrunchPngs = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "app_name", "Xed-Debug")
        }
    }

    flavorDimensions += "store"

    productFlavors {
        create("Fdroid") {
            dimension = "store"
            targetSdk = 28
        }

        create("PlayStore") {
            dimension = "store"
            targetSdk = 35
        }
    }

    //values in this will be overridden by the flavours
    defaultConfig {
        applicationId = "com.rk.xededitor"
        minSdk = 26

        //needed for running proot
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28

        //versioning
        versionCode = 53
        versionName = "3.1.0"
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
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }


}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core:main"))
}
