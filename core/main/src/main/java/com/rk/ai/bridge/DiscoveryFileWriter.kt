package com.rk.ai.bridge

import android.os.Process
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.AiConfig
import com.rk.ai.agents.AgentTypeRegistry
import com.rk.file.sandboxHomeDir
import com.rk.settings.Settings
import com.rk.utils.getTempDir
import java.io.File

object DiscoveryFileWriter {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val writeLock = Any()

    data class BridgeInfo(val host: String, val port: Int, val token: String, val workspacePath: String)

    fun write(info: BridgeInfo) {
        synchronized(writeLock) {
            val pid = Process.myPid()
            val url = "http://${info.host}:${info.port}"
            val config = JsonObject().apply {
                addProperty("url", url)
                addProperty("host", info.host)
                addProperty("port", info.port)
                addProperty("token", info.token)
                addProperty("authToken", info.token)
                addProperty("workspacePath", info.workspacePath)
                addProperty("pid", pid)
                add("ideInfo", JsonObject().apply {
                    addProperty("name", "xed-ide")
                    addProperty("displayName", "Xed Editor")
                })
            }
            for (agent in AgentTypeRegistry.available()) {
                writeAgentConfig(agent.name, info)
            }
            writeDiscoveryFiles(info, pid, url, config)
        }
    }

    fun forceWriteAgentConfigs(info: BridgeInfo) {
        for (agent in AgentTypeRegistry.available()) {
            writeAgentConfig(agent.name, info)
        }
    }

    private fun writeAgentConfig(agentName: String, info: BridgeInfo) {
        runCatching {
            val sandboxHome = sandboxHomeDir()
            if (!sandboxHome.exists()) sandboxHome.mkdirs()
            val configDir = File(sandboxHome, AiConfig.Discovery.agentConfigDir(agentName))
            configDir.mkdirs()
            val configFile = File(configDir, AiConfig.Discovery.agentConfigFile(agentName))
            buildAndWriteAgentConfig(agentName, info, configFile)
        }
    }

    private fun mergeAgentMcpConfig(agentName: String, info: BridgeInfo, targetDir: File) {
        runCatching {
            val configFile = File(targetDir, AiConfig.Discovery.agentConfigFile(agentName))
            buildAndWriteAgentConfig(agentName, info, configFile)
        }
    }

    private fun buildAndWriteAgentConfig(agentName: String, info: BridgeInfo, configFile: File) {
        val existing = if (configFile.exists()) {
            runCatching { JsonParser.parseString(configFile.readText()).asJsonObject }.getOrDefault(JsonObject())
        } else JsonObject()

        applyAgentDefaults(agentName, existing)
        val mcpKey = AiConfig.Discovery.agentMcpKey(agentName)
        val target = existing.getAsJsonObject(mcpKey) ?: JsonObject().also { existing.add(mcpKey, it) }
        val mcpEntry = buildMcpServerConfig(agentName, info)
        target.add("xed-ide", mcpEntry)

        if (agentName == "opencode") {
            existing.remove("mcpServers")
            existing.remove("apiKey")
        }

        configFile.writeText(gson.toJson(existing))
    }

    private fun applyAgentDefaults(agentName: String, config: JsonObject) {
        if (agentName == "gemini") {
            config.getAsJsonObject("general")?.apply { addProperty("preferredEditor", "vim") }
                ?: config.add("general", JsonObject().apply { addProperty("preferredEditor", "vim") })
            config.getAsJsonObject("ide")?.apply { addProperty("enabled", true); addProperty("hasSeenNudge", true) }
                ?: config.add("ide", JsonObject().apply { addProperty("enabled", true); addProperty("hasSeenNudge", true) })
            config.getAsJsonObject("privacy")?.apply { addProperty("usageStatisticsEnabled", false) }
                ?: config.add("privacy", JsonObject().apply { addProperty("usageStatisticsEnabled", false) })
            config.getAsJsonObject("telemetry")?.apply { addProperty("enabled", false) }
                ?: config.add("telemetry", JsonObject().apply { addProperty("enabled", false) })
            if (Settings.ai_api_key.isNotBlank()) {
                config.addProperty("apiKey", Settings.ai_api_key)
            }
        }
    }

    private fun buildMcpServerConfig(agentName: String, info: BridgeInfo): JsonObject {
        val headers = JsonObject().apply {
            addProperty("Authorization", "Bearer ${info.token}")
            addProperty("authorization", "Bearer ${info.token}")
            addProperty("x-ide-token", info.token)
        }
        val mcpUrl = "http://${info.host}:${info.port}/mcp?token=${info.token}"
        return if (agentName == "gemini") {
            JsonObject().apply {
                addProperty("url", mcpUrl)
                add("headers", headers)
            }
        } else {
            JsonObject().apply {
                addProperty("type", "remote")
                addProperty("url", mcpUrl)
                addProperty("enabled", true)
                addProperty("timeout", 120000)
                add("headers", headers)
            }
        }
    }

    private fun writeServerConfigJson(dir: File, prefix: String, pid: Int, port: Int, json: String) {
        dir.listFiles { file -> file.name.startsWith(prefix) && file.name.endsWith(".json") }
            ?.filter { file ->
                val parts = file.name.removePrefix(prefix).removeSuffix(".json").split("-")
                val filePort = parts.getOrNull(1)?.toIntOrNull()
                filePort != null && filePort != port
            }
            ?.forEach { it.delete() }
        File(dir, "$prefix$pid-${port}.json").writeText(json)
    }

    private fun writeIdeEnv(dir: File, info: BridgeInfo) {
        File(dir, "ide.env").writeText(buildString {
            appendLine("export IDE_SERVER_PORT=${info.port}")
            appendLine("export IDE_AUTH_TOKEN=${info.token}")
        })
        File(dir, "ide.json").writeText(gson.toJson(JsonObject().apply {
            addProperty("url", "http://${info.host}:${info.port}")
            addProperty("port", info.port)
            addProperty("token", info.token)
            addProperty("authToken", info.token)
            addProperty("workspacePath", info.workspacePath)
        }))
    }

    private fun writeDiscoveryFiles(info: BridgeInfo, pid: Int, url: String, config: JsonObject) {
        val tmpDir = getTempDir()
        val agentNames = AgentTypeRegistry.available().map { it.name }
        val json = gson.toJson(config)
        val dirs = mutableListOf<File>().apply {
            AiConfig.Discovery.discoveryDirs.forEach { add(File(tmpDir, it)) }
            add(File(AiConfig.Discovery.tmpDiscoveryDir))
            agentNames.forEach { agent ->
                add(File(tmpDir, "terminal/$agent-sheet/$agent/ide"))
            }
            info.workspacePath.split(File.pathSeparator).forEach { wp ->
                if (wp.isNotBlank()) {
                    add(File(wp, AiConfig.Discovery.xedIdeDir))
                    agentNames.forEach { agent -> add(File(wp, ".$agent")) }
                }
            }
        }
        dirs.forEach { dir ->
            runCatching {
                dir.mkdirs()
                val prefix = if (dir.name == "ide-bridge") "ide-server-" else "xed-ide-server-"
                writeServerConfigJson(dir, prefix, pid, info.port, json)
                if (dir.name == AiConfig.Discovery.xedIdeDir) {
                    writeIdeEnv(dir, info)
                }
                agentNames.forEach { agent ->
                    if (dir.name == ".$agent") mergeAgentMcpConfig(agent, info, dir)
                }
            }
        }
    }

    fun clearForProcess() {
        val tmpDir = getTempDir()
        val dirs = AiConfig.Discovery.discoveryDirs.map { File(tmpDir, it) } + File(AiConfig.Discovery.tmpDiscoveryDir)
        dirs.forEach { dir ->
            dir.listFiles { file -> file.name.endsWith(".json") && file.name.contains("-server-") }
                ?.forEach { it.delete() }
        }
    }
}
