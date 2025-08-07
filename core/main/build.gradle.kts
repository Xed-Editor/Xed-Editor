import java.io.ByteArrayOutputStream
import java.io.File
import groovy.json.JsonOutput

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

val runPrecompileScript by tasks.registering {
    group = "build setup"
    description = "Update supported_locales.json in assets"
    doFirst {
        val resDir = file("${project.rootDir}/core/resources/src/main/res")
        val outputFile = file("src/main/assets/supported_locales.json")
        // Ensure output directory exists
        outputFile.parentFile.mkdirs()

        val locales = mutableListOf<String>()

        resDir.listFiles { file -> file.isDirectory && file.name.startsWith("values") }?.forEach { dir ->
            val folderName = dir.name
            val locale = when {
                folderName == "values" -> "en"
                folderName.startsWith("values-") -> {
                    // Replace -r with -
                    folderName.removePrefix("values-").replace("-r", "-")
                }
                else -> null
            }
            locale?.let { locales.add(it) }
        }

        // Write JSON array to file
        outputFile.writeText(
            JsonOutput.prettyPrint(JsonOutput.toJson(locales))
        )

        println("✅ Generated ${outputFile.path}")
    }
}

tasks.named("preBuild") {
    dependsOn(runPrecompileScript)
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
    api(project(":core:terminal-view"))
    api(project(":core:terminal-emulator"))
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

    api(platform("io.github.Rosemoe.sora-editor:bom:0.23.6"))
    api("io.github.Rosemoe.sora-editor:editor")
    api("io.github.Rosemoe.sora-editor:editor-lsp")
    api("io.github.Rosemoe.sora-editor:language-textmate")
    api(libs.lsp4j)
    //api(project(":core:editor"))
    //api(project(":core:language-textmate"))
    api(project(":core:resources"))
    api(project(":core:components"))
    api(project(":core:bridge"))

    api(libs.kotlin.reflect)

}
