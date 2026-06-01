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
    ":core:ai",
    ":core:vibe-coding:ai-core",
    ":core:vibe-coding:ai-streaming",
    ":core:vibe-coding:ai-models",
    ":core:vibe-coding:ai-providers",
    ":core:vibe-coding:ai-mcp-client",
    ":core:vibe-coding:agent-runtime",
    ":core:vibe-coding:agent-tools-search",
    ":core:vibe-coding:ai-persistence",
    ":core:components",
    ":core:resources",
    ":core:terminal-view",
    ":core:terminal-emulator",
    ":core:extension",
)

val soraX = file("soraX")
val soraXModules = linkedMapOf(
    ":editor" to "editor",
    ":oniguruma-native" to "oniguruma-native",
    ":editor-lsp" to "editor-lsp",
    ":language-textmate" to "language-textmate",
)

val missingSoraXModules = soraXModules.values
    .map { File(soraX, it) }
    .filterNot { it.isDirectory }

if (missingSoraXModules.isNotEmpty()) {
    throw GradleException(
        buildString {
            appendLine("Missing soraX editor engine submodule.")
            appendLine("Expected directories:")
            missingSoraXModules.forEach { appendLine("  - ${it.path}") }
            appendLine()
            appendLine("Initialize it before running Gradle:")
            appendLine("  git submodule update --init --recursive")
            appendLine()
            appendLine("In GitHub Actions, run:")
            appendLine("  bash .github/scripts/ensure-sorax.sh")
        }
    )
}

soraXModules.forEach { (projectPath, moduleDir) ->
    include(projectPath)
    project(projectPath).projectDir = file("soraX/$moduleDir")
}

include(":baselineprofile", ":benchmark", ":benchmark2")
