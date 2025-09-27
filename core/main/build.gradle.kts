import groovy.json.JsonOutput

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
}

val gitCommitHash: Provider<String> = providers.exec {
    commandLine("git", "rev-parse", "--short=8", "HEAD")
}.standardOutput.asText.map { it.trim() }


val fullGitCommitHash: Provider<String> = providers.exec {
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map { it.trim() }

val gitCommitDate: Provider<String> = providers.exec {
    commandLine("git", "show", "-s", "--format=%cI", "HEAD")
}.standardOutput.asText.map { it.trim() }

android {
    namespace = "com.rk.xededitor"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${fullGitCommitHash.get()}\"")
            buildConfigField("String", "GIT_SHORT_COMMIT_HASH", "\"${gitCommitHash.get()}\"")
            buildConfigField("String", "GIT_COMMIT_DATE", "\"${gitCommitDate.get()}\"")

            isMinifyEnabled = false
            isShrinkResources = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }

        debug {
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${fullGitCommitHash.get()}\"")
            buildConfigField("String", "GIT_SHORT_COMMIT_HASH", "\"${gitCommitHash.get()}\"")
            buildConfigField("String", "GIT_COMMIT_DATE", "\"${gitCommitDate.get()}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "2.2.20"
    }
}

val runPrecompileScript by tasks.registering(GenerateSupportedLocales::class) {
    group = "build setup"
    description = "Update supported_locales.json in assets"

    resDir.set(layout.projectDirectory.dir("../resources/src/main/res"))

    outputFile.set(layout.projectDirectory.file("src/main/assets/supported_locales.json"))
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
    api(libs.utilcode)
    api(libs.coil.compose)
    api(libs.gson)
    api(libs.commons.net)
    api(libs.okhttp)
    api(libs.material.motion.compose.core)
    api(libs.nanohttpd)
    api(libs.photoview)
    api(libs.glide)
    api(libs.media3.ui)
    api(libs.browser)
    api(libs.quickjs.android)
    api(libs.anrwatchdog)
    api(libs.lsp4j)
    api(libs.kotlin.reflect)
    api(libs.androidx.documentfile)
    api(libs.androidx.material.icons.extended)

    // Modules
    api(project(":editor"))
    api(project(":editor-lsp"))
    api(project(":language-textmate"))
    api(project(":core:resources"))
    api(project(":core:components"))
    api(project(":core:bridge"))
    api(project(":core:extension"))
    api(project(":core:terminal-view"))
    api(project(":core:terminal-emulator"))
}

abstract class GenerateSupportedLocales : DefaultTask() {
    @get:InputDirectory
    abstract val resDir: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val locales = mutableListOf<String>()

        resDir.get().asFile.listFiles { file ->
            file.isDirectory && file.name.startsWith("values")
        }?.forEach { dir ->
            val folderName = dir.name
            val locale = when {
                folderName == "values" -> "en"
                folderName.startsWith("values-") ->
                    folderName.removePrefix("values-").replace("-r", "-")

                else -> null
            }
            locale?.let { locales.add(it) }
        }

        locales.sort()

        val outFile = outputFile.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(locales)))

        logger.lifecycle("✅ Generated ${outFile.path}")
    }
}

