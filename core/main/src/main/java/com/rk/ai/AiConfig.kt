package com.rk.ai

object AiConfig {
    val ignoredDirectories: Set<String> = setOf(
        ".git", ".gradle", ".idea", "build", "node_modules",
        ".dex", ".cache",
    )

    val fallbackWorkspaceRoots: List<String> = listOf(
        "/home", "/storage/emulated/0", "/sdcard",
    )

    object ProjectDetection {
        val configFiles = mapOf(
            "package.json" to Pair("JavaScript/TypeScript", "npm/yarn/pnpm"),
            "pubspec.yaml" to Pair("Dart", "pub"),
            "build.gradle.kts" to Pair("Kotlin/Java", "Gradle"),
            "build.gradle" to Pair("Kotlin/Java", "Gradle"),
            "Cargo.toml" to Pair("Rust", "Cargo"),
            "CMakeLists.txt" to Pair("C/C++", "CMake"),
            "go.mod" to Pair("Go", "go mod"),
        )
        val pythonIndicators = setOf("requirements.txt", "setup.py", "pyproject.toml")
    }
}
