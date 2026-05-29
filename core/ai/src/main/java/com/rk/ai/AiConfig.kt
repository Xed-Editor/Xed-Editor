package com.rk.ai

object AiConfig {
    val ignoredDirectories: Set<String> = setOf(
        ".git", ".gradle", ".idea", "build", "node_modules",
        ".dex", ".cache",
    )

    val fallbackWorkspaceRoots: List<String> = listOf(
        "/home", "/storage/emulated/0", "/sdcard",
    )

    val commonReuseRoots: Set<String> = setOf(
        "/", "/storage/emulated/0", "/home",
    )

    object Discovery {
        val openCodeConfigFile = "opencode.json"
        val openCodeMcpFile = "mcp.json"
        val geminiSettingsFile = "settings.json"
        val xedIdeDir = ".xed"
        val openCodeDir = ".opencode"
        val xedBridgeEnvFile = "xed-bridge.env"
        val ideEnvFile = "ide.env"
        val xedBridgeEnvHomeFile = ".xed-bridge.env"
        val launcherScriptFile = "launcher.sh"

        val discoveryDirs = listOf(
            "gemini/ide",
            "terminal/gemini-sheet/gemini/ide",
            "terminal/gemini-sheet/gemini/ide",
            "terminal/opencode-sheet/gemini/ide",
            "terminal/opencode-sheet/opencode/ide",
            "terminal/antigravity-sheet/antigravity/ide",
            "terminal/codex-sheet/codex/ide",
            "ide-bridge",
        )

        val tmpDiscoveryDir = "/tmp/xed-ide"
    }

    object Debug {
        val defaultDebugEnvValue = "true"
        val defaultDebugLogFile = "/home/.gemini/xed-debug.log"
        val defaultContextTraceDir = "/home/.gemini/xed-traces"
    }

    object Paths {
        const val linker32Bit = "/system/bin/linker64"
        const val linker64Bit = "/system/bin/linker"
        const val sandboxHomePath = "/home"
        const val binSh = "/system/bin/sh"
        const val binBash = "/bin/bash"
        const val sandboxBinary = "sandbox"
    }

    object Agents {
        const val geminiName = "gemini"
        const val opencodeName = "opencode"
        const val antigravityName = "antigravity"
        const val codexName = "codex"
    }

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
