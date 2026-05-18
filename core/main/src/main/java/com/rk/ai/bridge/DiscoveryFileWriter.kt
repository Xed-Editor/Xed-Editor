package com.rk.ai.bridge

import android.os.Process
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.AiConfig
import com.rk.ai.IdeBridge
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
            val json = gson.toJson(config)

            AgentTypeRegistry.available().forEach { agent ->
                writeAgentConfig(agent.name, info)
            }
            writeDiscoveryFiles(info, pid, url, config, json)
        }
    }

    private fun writeAgentConfig(agentName: String, info: BridgeInfo) {
        runCatching {
            val sandboxHome = sandboxHomeDir()
            if (!sandboxHome.exists()) sandboxHome.mkdirs()
            val configDir = File(sandboxHome, AiConfig.Discovery.agentConfigDir(agentName))
            configDir.mkdirs()
            val configFile = File(configDir, AiConfig.Discovery.agentConfigFile(agentName))
            val mcpKey = AiConfig.Discovery.agentMcpKey(agentName)

            val existing = if (configFile.exists()) {
                runCatching { JsonParser.parseString(configFile.readText()).asJsonObject }.getOrDefault(JsonObject())
            } else {
                JsonObject()
            }

            if (agentName == "gemini") {
                existing.getAsJsonObject("general")?.apply { addProperty("preferredEditor", "vim") }
                    ?: existing.add("general", JsonObject().apply { addProperty("preferredEditor", "vim") })
                existing.getAsJsonObject("ide")?.apply { addProperty("enabled", true); addProperty("hasSeenNudge", true) }
                    ?: existing.add("ide", JsonObject().apply { addProperty("enabled", true); addProperty("hasSeenNudge", true) })
                existing.getAsJsonObject("privacy")?.apply { addProperty("usageStatisticsEnabled", false) }
                    ?: existing.add("privacy", JsonObject().apply { addProperty("usageStatisticsEnabled", false) })
                existing.getAsJsonObject("telemetry")?.apply { addProperty("enabled", false) }
                    ?: existing.add("telemetry", JsonObject().apply { addProperty("enabled", false) })
                
                if (Settings.ai_api_key.isNotBlank()) {
                    existing.addProperty("apiKey", Settings.ai_api_key)
                }
            } else if (agentName == "opencode") {
                if (Settings.ai_api_key.isNotBlank()) {
                    existing.addProperty("apiKey", Settings.ai_api_key)
                }
            }

            val target = existing.getAsJsonObject(mcpKey)
                ?: JsonObject().also { existing.add(mcpKey, it) }
            
            val headers = JsonObject().apply {
                addProperty("Authorization", "Bearer ${info.token}")
                addProperty("authorization", "Bearer ${info.token}")
                addProperty("x-ide-token", info.token)
            }

            if (agentName == "gemini") {
                target.add("xed-ide", JsonObject().apply {
                    addProperty("url", "http://${info.host}:${info.port}/mcp")
                    add("headers", headers)
                })
                existing.getAsJsonObject("mcp")?.remove("xed-ide")
            } else {
                target.add("xed-ide", JsonObject().apply {
                    addProperty("type", "remote")
                    addProperty("url", "http://${info.host}:${info.port}/mcp")
                    addProperty("enabled", true)
                    addProperty("timeout", 120000)
                    add("headers", headers)
                })
                existing.getAsJsonObject("mcpServers")?.remove("xed-ide")
            }

            configFile.writeText(gson.toJson(existing))
        }
    }

    private fun writeServerConfigJson(dir: File, prefix: String, pid: Int, port: Int, json: String) {
        dir.listFiles { file -> file.name.startsWith(prefix) && file.name.endsWith(".json") }
            ?.filter { file ->
                val parts = file.name.removePrefix(prefix).removeSuffix(".json").split("-")
                val filePid = parts.firstOrNull()?.toIntOrNull()
                val filePort = parts.getOrNull(1)?.toIntOrNull()
                filePid == pid && filePort != null && filePort != port
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

    private fun mergeAgentMcpConfig(dir: File, agentName: String, info: BridgeInfo) {
        val mcpKey = AiConfig.Discovery.agentMcpKey(agentName)
        val configFileName = AiConfig.Discovery.agentConfigFile(agentName)
        val mcpFile = File(dir, configFileName)
        val existingMcp = runCatching { JsonParser.parseString(mcpFile.readText()).asJsonObject }.getOrDefault(JsonObject())
        val target = existingMcp.getAsJsonObject(mcpKey)
            ?: JsonObject().also { existingMcp.add(mcpKey, it) }

        val headers = JsonObject().apply {
            addProperty("Authorization", "Bearer ${info.token}")
            addProperty("authorization", "Bearer ${info.token}")
            addProperty("x-ide-token", info.token)
        }

        if (agentName == "gemini") {
            target.add("xed-ide", JsonObject().apply {
                addProperty("url", "http://${info.host}:${info.port}/mcp")
                add("headers", headers)
            })
            existingMcp.getAsJsonObject("mcp")?.remove("xed-ide")
            if (Settings.ai_api_key.isNotBlank()) {
                existingMcp.addProperty("apiKey", Settings.ai_api_key)
            }
        } else {
            target.add("xed-ide", JsonObject().apply {
                addProperty("type", "remote")
                addProperty("url", "http://${info.host}:${info.port}/mcp")
                addProperty("enabled", true)
                addProperty("timeout", 120000)
                add("headers", headers)
            })
            existingMcp.getAsJsonObject("mcpServers")?.remove("xed-ide")
            if (agentName == "opencode" && Settings.ai_api_key.isNotBlank()) {
                existingMcp.addProperty("apiKey", Settings.ai_api_key)
            }
        }
        mcpFile.writeText(gson.toJson(existingMcp))
    }

    private fun writeDiscoveryFiles(info: BridgeInfo, pid: Int, url: String, config: JsonObject, json: String) {
        val tmpDir = getTempDir()
        val agentNames = AgentTypeRegistry.available().map { it.name }
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
                    if (dir.name == ".$agent") mergeAgentMcpConfig(dir, agent, info)
                }
            }
        }
    }

    fun clearForProcess() {
        val pid = Process.myPid()
        val tmpDir = getTempDir()
        val dirs = AiConfig.Discovery.discoveryDirs.map { File(tmpDir, it) } + File(AiConfig.Discovery.tmpDiscoveryDir)
        dirs.forEach { dir ->
            dir.listFiles { file -> file.name.contains("-$pid-") && file.name.endsWith(".json") }
                ?.forEach { it.delete() }
        }
    }
}
