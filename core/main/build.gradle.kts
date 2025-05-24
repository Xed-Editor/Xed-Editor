import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
}

fun getGitCommitHash(): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--short=8", "HEAD")
        standardOutput = stdout
    }
    return stdout.toString().trim()
}

fun getGitCommitDate(): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "show", "-s", "--format=%cI", "HEAD")
        standardOutput = stdout
    }
    return stdout.toString().trim()
}

fun getFullGitCommitHash(): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "HEAD")
        standardOutput = stdout
    }
    return stdout.toString().trim()
}


android {
    namespace = "com.rk.xededitor"
    buildFeatures {
        buildConfig = true
    }

    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${getFullGitCommitHash()}\"")
            buildConfigField("String", "GIT_SHORT_COMMIT_HASH", "\"${getGitCommitHash()}\"")
            buildConfigField("String", "GIT_COMMIT_DATE", "\"${getGitCommitDate()}\"")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${getFullGitCommitHash()}\"")
            buildConfigField("String", "GIT_SHORT_COMMIT_HASH", "\"${getGitCommitHash()}\"")
            buildConfigField("String", "GIT_COMMIT_DATE", "\"${getGitCommitDate()}\"")
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
        kotlinCompilerExtensionVersion = "2.1.10"
    }


}

dependencies {
    api(libs.appcompat)
    api(libs.material)
    api(libs.constraintlayout)
    api(libs.navigation.fragment)
    api(libs.navigation.ui)
    api(libs.asynclayoutinflater)
    api(libs.navigation.fragment.ktx)
    api(libs.navigation.ui.ktx)
    api(libs.activity)
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
    //api(libs.org.eclipse.jgit)
    api(libs.gson)
    api(libs.commons.net)
    // api(libs.jcodings)
    // api(libs.joni)
    // api(libs.snakeyaml.engine)
    //api(libs.jdt.annotation)
    api(libs.okhttp)
    api(libs.material.motion.compose.core)
    api(libs.nanohttpd)
    api(libs.photoview)
    api(libs.glide)
    api(libs.media3.exoplayer)
    api(libs.media3.exoplayer.dash)
    api(libs.media3.ui)
    api(libs.browser)
    api(libs.quickjs.android)
    api(libs.anrwatchdog)
    api(libs.word.wrap)
    //api(libs.ktsh)

    //debug libs these libs doesnt get added when creating release builds
    debugApi(libs.bsh)
    debugApi(libs.leakcanary.android)

    api(platform("io.github.Rosemoe.sora-editor:bom:0.23.5-0d363ed-SNAPSHOT"))
    api("io.github.Rosemoe.sora-editor:editor")
    api("io.github.Rosemoe.sora-editor:language-textmate")
    //api(project(":core:editor"))
    //api(project(":core:language-textmate"))
    api(project(":core:resources"))
    api(project(":core:components"))
}
