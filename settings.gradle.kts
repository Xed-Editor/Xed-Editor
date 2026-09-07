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

include(":app", ":core:main", ":core:components", ":core:resources")

include(":features:terminal", ":features:extensions", ":features:runner", ":features:git")

include(":editor", ":oniguruma-native", ":editor-lsp", ":language-textmate")

project(":editor").projectDir = file("soraX/editor")

project(":oniguruma-native").projectDir = file("soraX/oniguruma-native")

project(":editor-lsp").projectDir = file("soraX/editor-lsp")

project(":language-textmate").projectDir = file("soraX/language-textmate")

include(":baselineprofile", ":benchmark", ":benchmark2")
include(":features:terminal:proot")
include(":features:terminal:link2symlink")
include(":features:terminal:xed-cli")
