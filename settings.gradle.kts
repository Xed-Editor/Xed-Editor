pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")

        maven("https://repo.eclipse.org/content/groups/releases/")
    }
    plugins {
        kotlin("jvm") version "2.1.10"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")

        maven("https://repo.eclipse.org/content/groups/releases/")
    }
}

rootProject.name = "Xed-Editor"
include(":app")
include(":core:main")

include(":core:components")
include(":core:resources")
include(":core:bridge")
include(":core:terminal-view")
include(":core:terminal-emulator")
include(":core:extension")

val soraX = file("soraX")

if (!soraX.exists() || soraX.listFiles()?.isEmpty() != false) {
    throw GradleException(
        """
        The 'soraX' submodule is missing or empty.
        
        Please run:
        
            git submodule update --init --recursive
        """.trimIndent()
    )
}


//includeBuild("soraX")
include(":editor")
project(":editor").projectDir = file("soraX/editor")

include(":editor-lsp")
project(":editor-lsp").projectDir = file("soraX/editor-lsp")

include(":language-textmate")
project(":language-textmate").projectDir = file("soraX/language-textmate")

include(":baselineprofile")
include(":benchmark")
include(":benchmark2")
