package com.rk.projects

import java.io.File

/** A project kind inferred from files on disk — works for created, imported, or cloned projects. */
enum class DetectedProjectType(val label: String) {
    FABRIC_MOD("Fabric mod"),
    FORGE_MOD("Forge / NeoForge mod"),
    ANDROID("Android app"),
    GRADLE("Gradle (JVM) project"),
    NODE("Node.js"),
    PYTHON("Python"),
    WEB("Static web"),
    RUST("Rust"),
    GO("Go"),
    UNKNOWN("Unknown"),
}

/**
 * Heuristic project-type detector. Inspects marker files in [root] (and one level of `app/`), so it
 * works regardless of whether the project was created here, imported, or cloned.
 */
object ProjectTypeDetector {

    fun detect(root: File): DetectedProjectType {
        if (!root.isDirectory) return DetectedProjectType.UNKNOWN

        fun has(rel: String) = File(root, rel).exists()
        fun topLevel(predicate: (File) -> Boolean) = root.listFiles()?.any { it.isFile && predicate(it) } == true

        // Minecraft mods first (most specific)
        if (has("src/main/resources/fabric.mod.json")) return DetectedProjectType.FABRIC_MOD
        if (
            has("src/main/resources/META-INF/mods.toml") ||
                has("src/main/resources/META-INF/neoforge.mods.toml")
        ) {
            return DetectedProjectType.FORGE_MOD
        }

        // Android
        val gradleFiles = listOf("build.gradle", "build.gradle.kts", "app/build.gradle", "app/build.gradle.kts")
        val hasAndroidPlugin =
            gradleFiles.any { rel ->
                val f = File(root, rel)
                f.exists() &&
                    runCatching { f.readText() }
                        .getOrDefault("")
                        .let { it.contains("com.android.application") || it.contains("com.android.library") }
            }
        if (hasAndroidPlugin || has("app/src/main/AndroidManifest.xml") || has("AndroidManifest.xml")) {
            return DetectedProjectType.ANDROID
        }

        // Generic Gradle (JVM)
        if (
            has("build.gradle") ||
                has("build.gradle.kts") ||
                has("settings.gradle") ||
                has("settings.gradle.kts") ||
                has("gradlew")
        ) {
            return DetectedProjectType.GRADLE
        }

        if (has("package.json")) return DetectedProjectType.NODE
        if (has("Cargo.toml")) return DetectedProjectType.RUST
        if (has("go.mod")) return DetectedProjectType.GO
        if (
            has("requirements.txt") ||
                has("pyproject.toml") ||
                has("setup.py") ||
                has("Pipfile") ||
                topLevel { it.extension == "py" }
        ) {
            return DetectedProjectType.PYTHON
        }
        if (has("index.html") || topLevel { it.extension == "html" }) return DetectedProjectType.WEB

        return DetectedProjectType.UNKNOWN
    }
}
