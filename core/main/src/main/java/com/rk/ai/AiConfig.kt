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
        val xedIdeDir = ".xed"
        val xedBridgeEnvFile = "xed-bridge.env"
        val ideEnvFile = "ide.env"
        val xedBridgeEnvHomeFile = ".xed-bridge.env"
        val launcherScriptFile = "launcher.sh"

        val discoveryDirs by lazy {
            com.rk.ai.agents.AgentTypeRegistry.available().flatMap { agent ->
                listOf(
                    "terminal/${agent.name}-sheet/${agent.name}/ide",
                    "${agent.name}/ide",
                )
            } + "ide-bridge"
        }

        val tmpDiscoveryDir = "/tmp/xed-ide"

        fun agentConfigDir(agentName: String) = when (agentName) {
            "gemini" -> ".gemini"
            "opencode" -> ".opencode"
            else -> ".config/$agentName"
        }
        fun agentConfigFile(agentName: String) = when (agentName) {
            "gemini" -> "settings.json"
            "opencode" -> "mcp.json"
            else -> "opencode.json"
        }
        fun agentMcpKey(agentName: String) = when (agentName) {
            "gemini" -> "mcpServers"
            else -> "mcp"
        }
    }

    object Debug {
        val defaultDebugEnvValue = "true"
        val defaultDebugLogFile = "/home/.xed/agent-debug.log"
        val defaultContextTraceDir = "/home/.xed/agent-traces"
    }

    object Paths {
        const val linker32Bit = "/system/bin/linker64"
        const val linker64Bit = "/system/bin/linker"
        const val sandboxHomePath = "/home"
        const val binSh = "/system/bin/sh"
        const val binBash = "/bin/bash"
        const val sandboxBinary = "sandbox"
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
