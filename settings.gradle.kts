rootProject.name = "Xed-Editor"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
        maven("https://repo.eclipse.org/content/groups/releases/")
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
        maven("https://repo.eclipse.org/content/groups/releases/")
    }
}

include(
    ":app",
    ":core:main",
    ":core:components",
    ":core:resources",
    ":core:terminal-view",
    ":core:terminal-emulator",
    ":core:extension",
)

val soraX = file("soraX")

if (!soraX.exists() || soraX.listFiles()?.isEmpty() != false) {
    throw GradleException(
        """
        The 'soraX' submodule is missing or empty.

        Please run:
            git submodule update --init --recursive
        """
            .trimIndent()
    )
}

include(":editor", ":oniguruma-native", ":editor-lsp", ":language-textmate")

project(":editor").projectDir = file("soraX/editor")

project(":oniguruma-native").projectDir = file("soraX/oniguruma-native")

project(":editor-lsp").projectDir = file("soraX/editor-lsp")

project(":language-textmate").projectDir = file("soraX/language-textmate")

include(":baselineprofile", ":benchmark", ":benchmark2")
