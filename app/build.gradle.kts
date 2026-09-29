import java.util.Properties

plugins {
    alias(libs.plugins.android.baselineprofile)
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktfmt)
}

val distributionDimension = "distribution"
val communityFlavor = "community"
val playStoreFlavor = "playstore"

android {
    namespace = "com.rk.application"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rk.xededitor"
        minSdk = 26

        targetSdk = 37

        // versioning
        versionCode = 107
        versionName = "3.4.5"
        vectorDrawables { useSupportLibrary = true }
    }

    flavorDimensions += distributionDimension

    productFlavors {
        // Default, fully featured distribution (GitHub, F-Droid, IzzyOnDroid).
        create(communityFlavor) {
            dimension = distributionDimension
            isDefault = true
            // Published at runtime as com.rk.app.AppFlavour via AppFlavour.init(BuildConfig.FLAVOUR).
            buildConfigField("String", "FLAVOUR", "\"$communityFlavor\"")
        }

        // Google Play distribution. Identical to community except that it does not bundle
        // the `:features:extensions` module.
        create(playStoreFlavor) {
            dimension = distributionDimension
            //using this id because I don't want to go through 14 day testing again
            applicationId = "com.rk.axion"
            buildConfigField("String", "FLAVOUR", "\"$playStoreFlavor\"")
        }
    }

    buildFeatures {
        viewBinding = true
        compose = false
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

    packaging { jniLibs { useLegacyPackaging = true } }

    signingConfigs {
        create("release") {
            val isGitHubActions = System.getenv("GITHUB_ACTIONS") == "true"

            val propertiesFilePath =
                if (isGitHubActions) {
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
                    if (isGitHubActions) {
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
            // Community keeps minification off; the playstore flavor enables it below.
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

// Play Store releases are minified; community releases stay unminified so extension work
//play store requires r8 to be enabled
androidComponents {
    beforeVariants(selector().withFlavor(distributionDimension, playStoreFlavor).withBuildType("release")) {
        it.isMinifyEnabled = true
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(libs.androidx.profileinstaller)
    coreLibraryDesugaring(libs.desugar)

    baselineProfile(project(":baselineprofile"))
    implementation(project(":core:main"))
    implementation(project(":core:resources"))
    implementation(libs.androidx.appcompat)
    implementation(project(":features:terminal"))
    implementation(project(":features:runner"))
    implementation(project(":features:git"))
    implementation(project(":features:ai"))

    // The extensions feature module is only bundled in the community distribution.
    // The playstore build intentionally ships without it due to play store policy
    "communityImplementation"(project(":features:extensions"))
}
