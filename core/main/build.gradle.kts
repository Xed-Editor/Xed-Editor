import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.rk.xededitor"

    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }


}

dependencies {
    api(libs.swiperefreshlayout)
    api(libs.appcompat)
    api(libs.material)
    api(libs.constraintlayout)
    api(libs.navigation.fragment)
    api(libs.navigation.ui)
    api(libs.asynclayoutinflater)
    api(libs.navigation.fragment.ktx)
    api(libs.navigation.ui.ktx)
    api(libs.activity)
    api(libs.lifecycle.livedata.ktx)
    api(libs.lifecycle.viewmodel.ktx)
    api(libs.lifecycle.runtime.ktx)
    api(libs.activity.compose)
    api(platform(libs.compose.bom))
    api(libs.ui)
    api(libs.ui.graphics)
    api(libs.material3)
    api(libs.navigation.compose)
    api(libs.terminal.view)
    api(libs.terminal.emulator)
    api(libs.utilcode)
    api(libs.coil.compose)
    api(libs.org.eclipse.jgit)
    api(libs.gson)
    api(libs.commons.net)
    api(libs.jcodings)
    api(libs.joni)
    api(libs.snakeyaml.engine)
    api(libs.jdt.annotation)
    api(libs.ktsh)
    api(libs.okhttp)
    api(libs.material.motion.compose.core)
    api(libs.nanohttpd)
    api(libs.photoview)
    api(libs.glide)
    api(libs.media3.exoplayer)
    api(libs.media3.exoplayer.dash)
    api(libs.media3.ui)
    api(libs.browser)
    api(libs.eventbus)
    api(libs.quickjs.android)
    api(libs.anrwatchdog)

    api(project(":core:runner"))
    api(project(":core:file"))
    api(project(":core:filetree"))
    api(project(":core:settings"))
    api(project(":core:commons"))
    api(project(":core:components"))
    api(project(":core:editor"))
    api(project(":core:crash-handler"))
    api(project(":core:language-textmate"))
    api(project(":core:resources"))
    api(project(":core:karbon-exec"))
    api(project(":core:mutator-engine"))
    api(project(":core:extension"))
}
