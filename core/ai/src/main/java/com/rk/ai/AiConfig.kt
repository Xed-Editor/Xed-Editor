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
        const val openCodeConfigFile = "opencode.json"
        const val openCodeMcpFile = "mcp.json"
        const val geminiSettingsFile = "settings.json"
        const val xedIdeDir = ".xed"
        const val openCodeDir = ".opencode"
        const val xedBridgeEnvFile = "xed-bridge.env"
        const val ideEnvFile = "ide.env"
        const val xedBridgeEnvHomeFile = ".xed-bridge.env"
        const val launcherScriptFile = "launcher.sh"
        const val mcpExternalServersFile = "mcp-servers.json"
        const val tokenFile = "mcp-token.txt"

        val discoveryDirs = listOf(
            "gemini/ide",
            "terminal/gemini-sheet/gemini/ide",
            "terminal/opencode-sheet/gemini/ide",
            "terminal/opencode-sheet/opencode/ide",
            "terminal/antigravity-sheet/antigravity/ide",
            "terminal/codex-sheet/codex/ide",
            "terminal/claude-sheet/claude/ide",
            "ide-bridge",
        )

        const val tmpDiscoveryDir = "/tmp/xed-ide"
    }

    object Debug {
        const val defaultDebugEnvValue = "true"
        const val defaultDebugLogFile = "/home/.gemini/xed-debug.log"
        const val defaultContextTraceDir = "/home/.gemini/xed-traces"
    }

    object Paths {
        const val linker32Bit = "/system/bin/linker"
        const val linker64Bit = "/system/bin/linker64"
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
        const val claudeName = "claude"
    }

    object ExternalMcp {
        const val maxServers = 10
        const val defaultTimeoutMs = 60_000L
        const val maxToolsPerServer = 50
        const val configFileDir = ".xed"
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
