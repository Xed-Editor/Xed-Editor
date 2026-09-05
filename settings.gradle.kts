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

include(":app", ":core:main", ":core:components", ":core:resources")

include(":features:terminal", ":features:extensions", ":features:runner", ":features:git")

include(":baselineprofile", ":benchmark", ":benchmark2")
include(":features:terminal:proot")
include(":features:terminal:link2symlink")
include(":features:terminal:xed-cli")

// The editor engine is the soraX submodule (a mirror of Rosemoe/sora-editor with
// Xed patches). It is compiled from source as an included build: the soraX:editor
// (and -lsp / -language-textmate / -oniguruma-native) placeholder coordinates from
// gradle/libs.versions.toml are substituted with the matching projects, so every
// build uses the checked-out soraX commit directly. No publishing is needed.
if (!file("soraX/settings.gradle.kts").exists()) {
    throw GradleException(
        """
        The 'soraX' submodule is missing or empty.

        Please run:
            git submodule update --init --recursive
        """
            .trimIndent()
    )
}

// The included soraX build is a standalone Gradle build, so it needs the SDK path in
// its own local.properties. Mirror the one from this project automatically.
if (!file("soraX/local.properties").exists()) {
    val parentProps = file("local.properties")
    if (parentProps.exists()) {
        try {
            parentProps.copyTo(file("soraX/local.properties"))
        } catch (ignored: Exception) {
            // fall back to ANDROID_HOME / ANDROID_SDK_ROOT when copying is not possible
        }
    }
}

includeBuild("soraX") {
    dependencySubstitution {
        // The "soraX:<module>" coordinates are placeholders (see libs.versions.toml).
        // They are never published or resolved from a repository - every Xed build
        // compiles these modules directly from the soraX submodule instead.
        substitute(module("soraX:editor")).using(project(":editor"))
        substitute(module("soraX:editor-lsp")).using(project(":editor-lsp"))
        substitute(module("soraX:language-textmate")).using(project(":language-textmate"))
        substitute(module("soraX:oniguruma-native")).using(project(":oniguruma-native"))
    }
}
