plugins {
    alias(libs.plugins.android.baselineprofile)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "com.rk.application"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rk.xededitor"
        minSdk = 26

        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28

        // versioning — read from root version.properties
        val versionProps = rootProject.file("version.properties")
            .takeIf { it.exists() }
            ?.readLines()
            ?.mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
            }
            ?.toMap() ?: emptyMap()

        versionCode = versionProps["versionCode"]?.toIntOrNull() ?: 87
        versionName = versionProps["versionName"] ?: "3.2.9"
        vectorDrawables { useSupportLibrary = true }
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }

    packaging {
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.md"
        }
    }

    signingConfigs {
        create("release") {
            val envPath = System.getenv("SIGNING_PROPERTIES_FILE")
            val propertiesFilePath = when {
                !envPath.isNullOrEmpty() -> envPath
                System.getenv("GITHUB_ACTIONS") == "true" -> "/tmp/signing.properties"
                else -> {
                    val localProps = rootProject.file("signing.properties")
                    if (localProps.exists()) localProps.absolutePath else ""
                }
            }

            val propertiesFile = File(propertiesFilePath)
            if (propertiesFile.exists()) {
                val props = propertiesFile.readLines().mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
                }.toMap()
                keyAlias = props["keyAlias"]
                keyPassword = props["keyPassword"]
                storePassword = props["storePassword"]
                storeFile = when {
                    System.getenv("GITHUB_ACTIONS") == "true" -> File("/tmp/xed.keystore")
                    !System.getenv("KEYSTORE_FILE").isNullOrEmpty() -> File(System.getenv("KEYSTORE_FILE"))
                    else -> props["storeFile"]?.let { File(it) }
                }
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
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    coreLibraryDesugaring(libs.desugar)

    baselineProfile(project(":baselineprofile"))
    implementation(project(":core:main"))
    implementation(project(":core:ai"))
    implementation(project(":core:vibe-coding:ai-integration"))
}
