package com.rk.ai.agent.files

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class XedConfig(
    val permission: List<XedPermissionRule> = emptyList(),
    val mcp: List<XedMcpConfig> = emptyList(),
    val model: XedModelConfig? = null,
    val instructions: String? = null,
    val tools: Map<String, Boolean> = emptyMap(),
)

data class XedPermissionRule(
    val tool: String = "*",
    val arg: String = "*",
    val action: String = "ask",
    val description: String = "",
)

data class XedMcpConfig(
    val name: String = "",
    val command: String = "",
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val disabled: Boolean = false,
)

data class XedModelConfig(
    val provider: String? = null,
    val model: String? = null,
)

object XedConfigLoader {
    private const val TAG = "XedConfigLoader"
    private const val CONFIG_FILE = "config.json"

    fun getConfigFile(workspacePath: String): File {
        return File(workspacePath, ".xed/$CONFIG_FILE")
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun loadConfig(workspacePath: String): XedConfig {
        val configFile = getConfigFile(workspacePath)
        if (!configFile.exists()) return XedConfig()

        return try {
            val raw = stripJsoncComments(configFile.readText())
            val root = json.parseToJsonElement(raw).jsonObject

            val permission = root["permission"]?.jsonArray?.map { element ->
                val obj = element.jsonObject
                XedPermissionRule(
                    tool = obj["tool"]?.jsonPrimitive?.contentOrNull ?: "*",
                    arg = obj["arg"]?.jsonPrimitive?.contentOrNull ?: "*",
                    action = obj["action"]?.jsonPrimitive?.contentOrNull ?: "ask",
                    description = obj["description"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            } ?: emptyList()

            val mcp = root["mcp"]?.jsonArray?.map { element ->
                val obj = element.jsonObject
                XedMcpConfig(
                    name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    command = obj["command"]?.jsonPrimitive?.contentOrNull ?: "",
                    args = obj["args"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    env = obj["env"]?.jsonObject?.entries?.associate {
                        it.key to (it.value.jsonPrimitive.contentOrNull ?: "")
                    } ?: emptyMap(),
                    disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            } ?: emptyList()

            val model: XedModelConfig? = root["model"]?.jsonObject?.let {
                XedModelConfig(
                    provider = it["provider"]?.jsonPrimitive?.contentOrNull,
                    model = it["model"]?.jsonPrimitive?.contentOrNull,
                )
            }

            val instructions = root["instructions"]?.jsonPrimitive?.contentOrNull

            val tools = root["tools"]?.jsonObject?.entries?.associate {
                it.key to (it.value.jsonPrimitive.booleanOrNull ?: true)
            } ?: emptyMap()

            XedConfig(
                permission = permission,
                mcp = mcp,
                model = model,
                instructions = instructions,
                tools = tools,
            ).also { Log.i(TAG, "Loaded config from $configFile") }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse config from $configFile", e)
            XedConfig()
        }
    }

    fun saveConfig(workspacePath: String, config: XedConfig): Boolean {
        return try {
            val configFile = getConfigFile(workspacePath)
            configFile.parentFile?.mkdirs()

            val sb = StringBuilder()
            sb.appendLine("{")

            if (config.permission.isNotEmpty()) {
                sb.appendLine("  \"permission\": [")
                config.permission.forEachIndexed { i, rule ->
                    sb.append("    { \"tool\": \"${escapeJson(rule.tool)}\", \"arg\": \"${escapeJson(rule.arg)}\", \"action\": \"${escapeJson(rule.action)}\"")
                    if (rule.description.isNotBlank()) sb.append(", \"description\": \"${escapeJson(rule.description)}\"")
                    sb.append(" }")
                    if (i < config.permission.lastIndex) sb.append(",")
                    sb.appendLine()
                }
                sb.appendLine("  ],")
            }

            if (config.mcp.isNotEmpty()) {
                sb.appendLine("  \"mcp\": [")
                config.mcp.forEachIndexed { i, mcpEntry ->
                    sb.append("    { \"name\": \"${escapeJson(mcpEntry.name)}\", \"command\": \"${escapeJson(mcpEntry.command)}\"")
                    if (mcpEntry.args.isNotEmpty()) {
                        sb.append(", \"args\": [")
                        mcpEntry.args.forEachIndexed { j, arg ->
                            sb.append("\"${escapeJson(arg)}\"")
                            if (j < mcpEntry.args.lastIndex) sb.append(", ")
                        }
                        sb.append("]")
                    }
                    if (mcpEntry.env.isNotEmpty()) {
                        sb.append(", \"env\": {")
                        mcpEntry.env.entries.forEachIndexed { j, (k, v) ->
                            sb.append("\"${escapeJson(k)}\": \"${escapeJson(v)}\"")
                            if (j < mcpEntry.env.size - 1) sb.append(", ")
                        }
                        sb.append("}")
                    }
                    if (mcpEntry.disabled) sb.append(", \"disabled\": true")
                    sb.append(" }")
                    if (i < config.mcp.lastIndex) sb.append(",")
                    sb.appendLine()
                }
                sb.appendLine("  ],")
            }

            if (config.model != null) {
                sb.appendLine("  \"model\": {")
                config.model.provider?.let { sb.appendLine("    \"provider\": \"${escapeJson(it)}\",") }
                config.model.model?.let { sb.appendLine("    \"model\": \"${escapeJson(it)}\",") }
                sb.appendLine("  },")
            }

            config.instructions?.let {
                sb.appendLine("  \"instructions\": \"${escapeJson(it)}\",")
            }

            if (config.tools.isNotEmpty()) {
                sb.appendLine("  \"tools\": {")
                config.tools.entries.forEachIndexed { i, (key, value) ->
                    sb.append("    \"${escapeJson(key)}\": $value")
                    if (i < config.tools.size - 1) sb.append(",")
                    sb.appendLine()
                }
                sb.appendLine("  },")
            }

            sb.appendLine("}")
            configFile.writeText(sb.toString())
            Log.i(TAG, "Saved config to $configFile")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save config", e)
            false
        }
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun stripJsoncComments(json: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < json.length) {
            when {
                json[i] == '/' && i + 1 < json.length && json[i + 1] == '/' -> {
                    while (i < json.length && json[i] != '\n') i++
                }
                json[i] == '/' && i + 1 < json.length && json[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < json.length && !(json[i] == '*' && json[i + 1] == '/')) i++
                    i += 2
                }
                json[i] == '"' -> {
                    sb.append('"')
                    i++
                    while (i < json.length && !(json[i] == '"' && (i == 0 || json[i - 1] != '\\'))) {
                        if (json[i] == '\\' && i + 1 < json.length) {
                            sb.append(json[i])
                            i++
                        }
                        sb.append(json[i])
                        i++
                    }
                    if (i < json.length) {
                        sb.append('"')
                        i++
                    }
                }
                else -> {
                    sb.append(json[i])
                    i++
                }
            }
        }
        return sb.toString()
    }
}
