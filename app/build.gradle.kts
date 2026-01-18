import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "com.rk.application"
    compileSdk = 36

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            val isGITHUB_ACTION = System.getenv("GITHUB_ACTIONS") == "true"

            val propertiesFilePath =
                if (isGITHUB_ACTION) {
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
                storeFile =
                    if (isGITHUB_ACTION) {
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
            isShrinkResources = false

            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "app_name", "Xed-Debug")
        }

        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    // Values in this will be overridden by the flavours
    defaultConfig {
        applicationId = "com.rk.xededitor"
        minSdk = 26

        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28

        // versioning
        versionCode = 81
        versionName = "3.2.6"
        vectorDrawables { useSupportLibrary = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    "baselineProfile"(project(":baselineprofile"))
    implementation(project(":core:main"))
}
