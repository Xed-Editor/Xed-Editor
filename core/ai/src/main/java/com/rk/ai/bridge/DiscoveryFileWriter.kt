package com.rk.ai.bridge

import android.os.Process
import com.rk.ai.AiConfig
import com.rk.file.sandboxHomeDir
import com.rk.utils.getTempDir
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object DiscoveryFileWriter {

    private val jsonFormat = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val writeLock = Any()

    data class BridgeInfo(val host: String, val port: Int, val token: String, val workspacePath: String)

    fun write(info: BridgeInfo) {
        synchronized(writeLock) {
            val pid = Process.myPid()
            val url = "http://${info.host}:${info.port}"
            
            val config = buildJsonObject {
                put("url", url)
                put("host", info.host)
                put("port", info.port)
                put("token", info.token)
                put("authToken", info.token)
                put("workspacePath", info.workspacePath)
                put("pid", pid)
                putJsonObject("ideInfo") {
                    put("name", "xed-ide")
                    put("displayName", "Xed Editor")
                }
            }
            val jsonText = jsonFormat.encodeToString(config)

            writeOpenCodeConfig(info)
            writeGeminiConfig(info)
            writeCodexConfig(info)
            writeAntigravityConfig(info)
            writeDiscoveryFiles(info, pid, url, jsonText)
        }
    }

    private fun writeOpenCodeConfig(info: BridgeInfo) {
        runCatching {
            val configDir = sandboxHomeDir().let { File(it, ".config/opencode") }
            configDir.mkdirs()
            val configFile = File(configDir, AiConfig.Discovery.openCodeConfigFile)
            
            val existingElement = runCatching { jsonFormat.parseToJsonElement(configFile.readText()) }.getOrNull()
            val existingObj = existingElement?.jsonObject ?: buildJsonObject {}
            
            val newObj = buildJsonObject {
                existingObj.forEach { key, value ->
                    if (key != "mcp" && key != "mcpServers") put(key, value)
                }

                val existingMcpServers = existingObj["mcpServers"]?.jsonObject
                if (existingMcpServers != null) {
                    putJsonObject("mcpServers") {
                        existingMcpServers.forEach { key, value ->
                            if (key != "xed-ide") put(key, value)
                        }
                    }
                }
                
                val existingMcp = existingObj["mcp"]?.jsonObject ?: buildJsonObject {}
                putJsonObject("mcp") {
                    existingMcp.forEach { key, value ->
                        if (key != "xed-ide") put(key, value)
                    }
                    putJsonObject("xed-ide") {
                        put("type", "remote")
                        put("url", "http://${info.host}:${info.port}/mcp")
                        put("enabled", true)
                        putJsonObject("headers") {
                            put("Authorization", "Bearer ${info.token}")
                        }
                    }
                }
            }
            configFile.writeText(jsonFormat.encodeToString(newObj))
        }
    }

    private fun writeGeminiConfig(info: BridgeInfo) {
        runCatching {
            val geminiDir = sandboxHomeDir().let { File(it, ".gemini") }
            geminiDir.mkdirs()
            val settingsFile = File(geminiDir, AiConfig.Discovery.geminiSettingsFile)
            
            if (!settingsFile.exists()) {
                settingsFile.writeText(jsonFormat.encodeToString(buildJsonObject {}))
            }
            
            val existingElement = runCatching { jsonFormat.parseToJsonElement(settingsFile.readText()) }.getOrNull()
            val existingObj = existingElement?.jsonObject ?: buildJsonObject {}
            
            val newObj = buildJsonObject {
                existingObj.forEach { key, value ->
                    if (key != "mcpServers" && key != "mcp") put(key, value)
                }
                
                val existingMcpServers = existingObj["mcpServers"]?.jsonObject ?: buildJsonObject {}
                putJsonObject("mcpServers") {
                    existingMcpServers.forEach { key, value ->
                        if (key != "xed-ide") put(key, value)
                    }
                    putJsonObject("xed-ide") {
                        put("url", "http://${info.host}:${info.port}/mcp")
                        putJsonObject("headers") {
                            put("Authorization", "Bearer ${info.token}")
                        }
                    }
                }
                
                val existingMcp = existingObj["mcp"]?.jsonObject
                if (existingMcp != null) {
                    putJsonObject("mcp") {
                        existingMcp.forEach { key, value ->
                            if (key != "xed-ide") put(key, value)
                        }
                    }
                }
            }
            settingsFile.writeText(jsonFormat.encodeToString(newObj))
        }
    }


    private fun writeCodexConfig(info: BridgeInfo) {
        runCatching {
            val configDir = sandboxHomeDir().let { File(it, ".codex") }
            configDir.mkdirs()
            val configFile = File(configDir, "config.toml")
            val section = "[mcp_servers.xed-ide]"
            val replacement = listOf(
                section,
                "url = \"http://${info.host}:${info.port}/mcp\"",
                "http_headers = { Authorization = \"Bearer ${info.token}\" }",
            )
            val lines = runCatching { configFile.readLines() }.getOrDefault(emptyList())
            val out = mutableListOf<String>()
            var index = 0
            var replaced = false
            while (index < lines.size) {
                if (lines[index].trim() == section) {
                    if (out.lastOrNull()?.isNotBlank() == true) out.add("")
                    out.addAll(replacement)
                    replaced = true
                    index++
                    while (index < lines.size && !lines[index].trimStart().startsWith("[")) index++
                    continue
                }
                out.add(lines[index])
                index++
            }
            if (!replaced) {
                if (out.lastOrNull()?.isNotBlank() == true) out.add("")
                out.addAll(replacement)
            }
            configFile.writeText(out.joinToString("\n").trimEnd() + "\n")
        }
    }

    private fun writeAntigravityConfig(info: BridgeInfo) {
        val configDirs = listOf(
            sandboxHomeDir().let { File(it, ".gemini/antigravity-cli") },
            sandboxHomeDir().let { File(it, ".gemini/config") },
            sandboxHomeDir().let { File(it, ".config/agy") }
        )
        for (configDir in configDirs) {
            runCatching {
                configDir.mkdirs()
                val configFile = File(configDir, "mcp_config.json")

                val existingElement = runCatching { jsonFormat.parseToJsonElement(configFile.readText()) }.getOrNull()
                val existingObj = existingElement?.jsonObject ?: buildJsonObject {}

                val newObj = buildJsonObject {
                    existingObj.forEach { key, value ->
                        if (key != "mcpServers") put(key, value)
                    }

                    val existingMcpServers = existingObj["mcpServers"]?.jsonObject ?: buildJsonObject {}
                    putJsonObject("mcpServers") {
                        existingMcpServers.forEach { key, value ->
                            if (key != "xed-ide") put(key, value)
                        }
                        putJsonObject("xed-ide") {
                            put("serverUrl", "http://${info.host}:${info.port}/mcp")
                            put("url", "http://${info.host}:${info.port}/mcp")
                            put("type", "remote")
                            put("enabled", true)
                            putJsonObject("headers") {
                                put("Authorization", "Bearer ${info.token}")
                            }
                        }
                    }
                }
                configFile.writeText(jsonFormat.encodeToString(newObj))
            }
        }
    }


    private fun writeDiscoveryFiles(info: BridgeInfo, pid: Int, url: String, jsonText: String) {
        val tmpDir = getTempDir()
        val agentSheetDirs = listOf("gemini", "opencode", "antigravity", "codex")
        val dirs = mutableListOf<File>().apply {
            AiConfig.Discovery.discoveryDirs.forEach { add(File(tmpDir, it)) }
            add(File(AiConfig.Discovery.tmpDiscoveryDir))
            agentSheetDirs.forEach { agent ->
                add(File(tmpDir, "terminal/$agent-sheet/$agent/ide"))
            }
            info.workspacePath.split(File.pathSeparator).forEach { wp ->
                if (wp.isNotBlank()) {
                    add(File(wp, AiConfig.Discovery.xedIdeDir))
                    add(File(wp, AiConfig.Discovery.openCodeDir))
                }
            }
        }
        dirs.forEach { dir ->
            runCatching {
                dir.mkdirs()
                val prefix = if (dir.name == "ide-bridge") "ide-server-" else "gemini-ide-server-"
                dir.listFiles { file -> file.name.startsWith(prefix) && file.name.endsWith(".json") }
                    ?.forEach { file ->
                        val parts = file.name.removePrefix(prefix).removeSuffix(".json").split("-")
                        val filePid = parts.firstOrNull()?.toIntOrNull()
                        if (filePid != null) {
                            if (filePid == pid) {
                                val filePort = parts.getOrNull(1)?.toIntOrNull()
                                if (filePort != null && filePort != info.port) file.delete()
                            } else if (!isPidAlive(filePid)) file.delete()
                        }
                    }
                val fileName = if (dir.name == "ide-bridge") "ide-server-$pid-${info.port}.json" else "gemini-ide-server-$pid-${info.port}.json"
                File(dir, fileName).writeText(jsonText)
                if (dir.name == AiConfig.Discovery.xedIdeDir) {
                    File(dir, "ide.json").writeText(jsonText)
                    File(dir, "ide.env").writeText(buildString {
                        appendLine("export XED_IDE_URL=$url"); appendLine("export XED_IDE_HOST=${info.host}")
                        appendLine("export XED_IDE_PORT=${info.port}"); appendLine("export XED_IDE_AUTH_TOKEN=${info.token}")
                        appendLine("export IDE_SERVER_PORT=${info.port}"); appendLine("export IDE_AUTH_TOKEN=${info.token}")
                    })
                }
                if (dir.name == AiConfig.Discovery.openCodeDir) {
                    val existingElement = runCatching { jsonFormat.parseToJsonElement(File(dir, AiConfig.Discovery.openCodeMcpFile).readText()) }.getOrNull()
                    val existingMcpObj = existingElement?.jsonObject ?: buildJsonObject {}
                    
                    val newObj = buildJsonObject {
                        existingMcpObj.forEach { key, value ->
                            if (key != "mcpServers" && key != "mcp") put(key, value)
                        }
                        
                        val existingMcp = existingMcpObj["mcp"]?.jsonObject ?: buildJsonObject {}
                        putJsonObject("mcp") {
                            existingMcp.forEach { key, value ->
                                if (key != "xed-ide") put(key, value)
                            }
                            putJsonObject("xed-ide") {
                                put("type", "remote")
                                put("url", "$url/mcp")
                                put("enabled", true)
                                putJsonObject("headers") {
                                    put("Authorization", "Bearer ${info.token}")
                                }
                            }
                        }
                    }
                    File(dir, "mcp.json").writeText(jsonFormat.encodeToString(newObj))
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

    private fun isPidAlive(pid: Int): Boolean =
        runCatching { File("/proc/$pid").exists() }.getOrDefault(true)
}