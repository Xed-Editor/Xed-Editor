package com.rk.ai.bridge

import android.os.Process
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.IdeBridge
import com.rk.file.sandboxHomeDir
import com.rk.utils.getTempDir
import java.io.File

object DiscoveryFileWriter {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class BridgeInfo(val host: String, val port: Int, val token: String, val workspacePath: String)

    fun write(info: BridgeInfo) {
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

        writeOpenCodeConfig(info)
        writeGeminiConfig(info)
        writeDiscoveryFiles(info, pid, url, config, json)
    }

    private fun writeOpenCodeConfig(info: BridgeInfo) {
        runCatching {
            val configDir = sandboxHomeDir().let { File(it, ".config/opencode") }
            configDir.mkdirs()
            val configFile = File(configDir, "opencode.json")
            val existing = runCatching { JsonParser.parseString(configFile.readText()).asJsonObject }.getOrDefault(JsonObject())
            existing.remove("mcpServers")
            val mcp = existing.getAsJsonObject("mcp") ?: JsonObject().also { existing.add("mcp", it) }
            mcp.add("xed-ide", JsonObject().apply {
                addProperty("type", "remote"); addProperty("url", "http://${info.host}:${info.port}/mcp"); addProperty("enabled", true)
                add("headers", JsonObject().apply { addProperty("Authorization", "Bearer ${info.token}") })
            })
            configFile.writeText(gson.toJson(existing))
        }
    }

    private fun writeGeminiConfig(info: BridgeInfo) {
        runCatching {
            val geminiDir = sandboxHomeDir().let { File(it, ".gemini") }
            geminiDir.mkdirs()
            val settingsFile = File(geminiDir, "settings.json")
            val existing = runCatching { JsonParser.parseString(settingsFile.readText()).asJsonObject }.getOrDefault(JsonObject())
            existing.remove("mcpServers")
            val mcp = existing.getAsJsonObject("mcp") ?: JsonObject().also { existing.add("mcp", it) }
            mcp.add("xed-ide", JsonObject().apply {
                addProperty("type", "remote"); addProperty("url", "http://${info.host}:${info.port}/mcp"); addProperty("enabled", true)
                add("headers", JsonObject().apply { addProperty("Authorization", "Bearer ${info.token}") })
            })
            existing.getAsJsonObject("general")?.apply { addProperty("preferredEditor", "vim") }
                ?: existing.add("general", JsonObject().apply { addProperty("preferredEditor", "vim") })
            existing.getAsJsonObject("ide")?.apply { addProperty("enabled", true); addProperty("hasSeenNudge", true) }
                ?: existing.add("ide", JsonObject().apply { addProperty("enabled", true); addProperty("hasSeenNudge", true) })
            settingsFile.writeText(gson.toJson(existing))
        }
    }

    private fun writeDiscoveryFiles(info: BridgeInfo, pid: Int, url: String, config: JsonObject, json: String) {
        val tmpDir = getTempDir()
        val agentSheetDirs = listOf("gemini", "opencode")
        val dirs = mutableListOf<File>().apply {
            add(File(tmpDir, "gemini/ide"))
            add(File(tmpDir, "terminal/gemini-sheet/gemini/ide"))
            agentSheetDirs.forEach { agent ->
                add(File(tmpDir, "terminal/$agent-sheet/gemini/ide"))
                add(File(tmpDir, "terminal/$agent-sheet/$agent/ide"))
            }
            add(File(tmpDir, "ide-bridge"))
            add(File("/tmp", "xed-ide"))
            info.workspacePath.split(File.pathSeparator).forEach { wp ->
                if (wp.isNotBlank()) {
                    add(File(wp, ".xed"))
                    add(File(wp, ".opencode"))
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
                File(dir, fileName).writeText(json)
                if (dir.name == ".xed") {
                    File(dir, "ide.json").writeText(json)
                    File(dir, "ide.env").writeText(buildString {
                        appendLine("export XED_IDE_URL=$url"); appendLine("export XED_IDE_HOST=${info.host}")
                        appendLine("export XED_IDE_PORT=${info.port}"); appendLine("export XED_IDE_AUTH_TOKEN=${info.token}")
                        appendLine("export IDE_SERVER_PORT=${info.port}"); appendLine("export IDE_AUTH_TOKEN=${info.token}")
                    })
                }
                if (dir.name == ".opencode") {
                    val existingMcp = runCatching { JsonParser.parseString(File(dir, "mcp.json").readText()).asJsonObject }.getOrDefault(JsonObject())
                    existingMcp.remove("mcpServers")
                    val mcp = existingMcp.getAsJsonObject("mcp") ?: JsonObject().also { existingMcp.add("mcp", it) }
                    mcpServers.add("xed-ide", JsonObject().apply {
                        addProperty("type", "remote"); addProperty("url", "$url/mcp"); addProperty("enabled", true)
                        add("headers", JsonObject().apply { addProperty("Authorization", "Bearer ${info.token}") })
                    })
                    File(dir, "mcp.json").writeText(gson.toJson(existingMcp))
                }
            }
        }
    }

    fun clearForProcess() {
        val pid = Process.myPid()
        val tmpDir = getTempDir()
        val dirs = listOf(
            File(tmpDir, "gemini/ide"),
            File(tmpDir, "terminal/gemini-sheet/gemini/ide"),
            File(tmpDir, "terminal/opencode-sheet/gemini/ide"),
            File(tmpDir, "terminal/opencode-sheet/opencode/ide"),
            File(tmpDir, "ide-bridge"),
            File("/tmp", "xed-ide"),
        )
        dirs.forEach { dir ->
            dir.listFiles { file -> file.name.contains("-$pid-") && file.name.endsWith(".json") }
                ?.forEach { it.delete() }
        }
    }

    private fun isPidAlive(pid: Int): Boolean =
        runCatching { File("/proc/$pid").exists() }.getOrDefault(true)
}
