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

fun initSoraX(): Boolean {
    if (soraX.exists() && soraX.listFiles()?.isNotEmpty() == true) return true
    val gitDir = file(".git")
    if (gitDir.exists()) {
        try {
            val pb = ProcessBuilder(
                "git", "submodule", "update", "--init", "--recursive", "--depth=1"
            )
            pb.directory(rootProject.projectDir)
            pb.environment()["GIT_REDIRECT_STDERR"] = "2>&1"
            val proc = pb.start()
            val exit = proc.waitFor(2, java.util.concurrent.TimeUnit.MINUTES)
            if (exit && proc.exitValue() == 0) return true
        } catch (_: Exception) {}
    }
    return false
}

if (!initSoraX()) {
    logger.warn("soraX submodule not available - editor will not compile without it. Run: git submodule update --init --recursive")
}

if (soraX.exists() && soraX.listFiles()?.isNotEmpty() == true) {
    include(":editor", ":oniguruma-native", ":editor-lsp", ":language-textmate")
    project(":editor").projectDir = file("soraX/editor")
    project(":oniguruma-native").projectDir = file("soraX/oniguruma-native")
    project(":editor-lsp").projectDir = file("soraX/editor-lsp")
    project(":language-textmate").projectDir = file("soraX/language-textmate")
} else {
    logger.warn("Proceeding without soraX modules. Build will fail if they are required dependencies.")
}

include(":baselineprofile", ":benchmark", ":benchmark2")
