import groovy.json.JsonOutput

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktfmt)
}

val gitCommitHash: Provider<String> =
    providers.exec { commandLine("git", "rev-parse", "--short=8", "HEAD") }.standardOutput.asText.map { it.trim() }

val fullGitCommitHash: Provider<String> =
    providers.exec { commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.map { it.trim() }

val gitCommitDate: Provider<String> =
    providers.exec { commandLine("git", "show", "-s", "--format=%cI", "HEAD") }.standardOutput.asText.map { it.trim() }

android {
    namespace = "com.rk.xededitor"
    compileSdk = 36

    buildFeatures { buildConfig = true }

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

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        debug {
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${fullGitCommitHash.get()}\"")
            buildConfigField("String", "GIT_SHORT_COMMIT_HASH", "\"${gitCommitHash.get()}\"")
            buildConfigField("String", "GIT_COMMIT_DATE", "\"${gitCommitDate.get()}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions { kotlinCompilerExtensionVersion = "2.2.20" }
}

val runPrecompileScript by
    tasks.registering(GenerateSupportedLocales::class) {
        group = "build setup"
        description = "Update supported_locales.json in assets"

        resDir.set(layout.projectDirectory.dir("../resources/src/main/res"))

        outputFile.set(layout.projectDirectory.file("src/main/assets/supported_locales.json"))
    }

tasks.named("preBuild") { dependsOn(runPrecompileScript) }

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.activity)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.material3)
    implementation(libs.navigation.compose)
    implementation(libs.utilcode)
    implementation(libs.coil.compose)
    implementation(libs.gson)
    implementation(libs.commons.net)
    implementation(libs.okhttp)
    implementation(libs.material.motion.compose.core)
    implementation(libs.nanohttpd)
    implementation(libs.photoview)
    implementation(libs.glide)
    implementation(libs.media3.ui)
    implementation(libs.browser)
    implementation(libs.quickjs.android)
    implementation(libs.anrwatchdog)
    implementation(libs.lsp4j)
    implementation(libs.kotlin.reflect)
    implementation(libs.androidx.documentfile)
    implementation(libs.compose.dnd)
    implementation(libs.androidx.material.icons.core)
    // implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Modules
    implementation(project(":editor"))
    implementation(project(":editor-lsp"))
    implementation(project(":language-textmate"))
    implementation(project(":core:resources"))
    implementation(project(":core:components"))
    // implementation(project(":core:extension"))
    implementation(project(":core:terminal-view"))
    implementation(project(":core:terminal-emulator"))
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidsvg.aar)
}

abstract class GenerateSupportedLocales : DefaultTask() {
    @get:InputDirectory abstract val resDir: DirectoryProperty

    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val locales = mutableListOf<String>()

        resDir
            .get()
            .asFile
            .listFiles { file -> file.isDirectory && file.name.startsWith("values") }
            ?.forEach { dir ->
                val folderName = dir.name
                val locale =
                    when {
                        folderName == "values" -> "en"
                        folderName.startsWith("values-") -> folderName.removePrefix("values-").replace("-r", "-")

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
