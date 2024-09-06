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
                keyAlias = properties["keyAlias"] as String
                keyPassword = properties["keyPassword"] as String
                if (isGITHUB_ACTION) {
                    storeFile = File("/tmp/xed.keystore")
                } else {
                    storeFile = File(properties["storeFile"] as String)
                }

                storePassword = properties["storePassword"] as String
            } else {
                println("Signing properties file not found at $propertiesFilePath")
            }
        }
    }


    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isCrunchPngs = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }


    }

    defaultConfig {
        applicationId = "com.rk.xededitor"
        minSdk = 26
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 31
        versionName = "2.7.0"
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

   // implementation("org.apache.commons:commons-vfs2:2.9.0")
    //implementation("com.github.mwiede:jsch:0.2.8")

    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.terminal.view)
    implementation(libs.terminal.emulator)
    implementation(libs.utilcode)
    implementation(project(":libsettings"))
    implementation(project(":libPlugin"))
    implementation(project(":libRunner"))
    implementation(project(":libEditor"))
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


}
