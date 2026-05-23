import groovy.json.JsonOutput

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
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

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
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
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.utilcode)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.gson)
    implementation(libs.commons.net)
    implementation(libs.okhttp)
    implementation(libs.material.motion.compose)
    implementation(libs.nanohttpd)
    implementation(libs.photoview)
    implementation(libs.glide)
    implementation(libs.anrwatchdog)
    implementation(libs.lsp4j)
    implementation(libs.kotlin.reflect)
    implementation(libs.androidx.documentfile)
    implementation(libs.compose.dnd)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidsvg.aar)
    implementation(libs.ec4j.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jgit)
    debugImplementation(libs.leakcanary)
    // implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // Modules
    implementation(project(":core:resources"))
    implementation(project(":core:components"))
    implementation(project(":core:extension"))
    implementation(project(":core:terminal-view"))
    implementation(project(":core:terminal-emulator"))
    implementation(project(":editor"))
    implementation(project(":editor-lsp"))
    implementation(project(":language-textmate"))
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
