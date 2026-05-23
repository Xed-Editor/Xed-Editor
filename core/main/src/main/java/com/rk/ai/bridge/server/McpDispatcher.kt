package com.rk.ai.bridge.server

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class McpDispatcher(private val toolRegistry: () -> McpToolRegistry) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun dispatch(
        id: JsonElement,
        method: String,
        request: JsonObject,
        context: McpToolContext? = null,
    ): String = withContext(Dispatchers.IO) {
        when (method) {
            "initialize" -> initializeResult(id, request)
            "tools/list" -> toolsListResult(id)
            "tools/call" -> toolsCallResult(id, request, context)
            "notifications/initialized" -> resultJson(id, JsonObject())
            "ping" -> resultJson(id, JsonObject())
            else -> errorJson(id, -32601, "method not found: $method")
        }
    }

    private suspend fun toolsCallResult(id: JsonElement, request: JsonObject, context: McpToolContext?): String {
        val params = request.getAsJsonObject("params") ?: return errorJson(id, -32602, "missing params")
        val name = params.get("name")?.asString.orEmpty()
        val args = params.getAsJsonObject("arguments") ?: JsonObject()
        val toolContext = context ?: return errorJson(id, -32603, "no tool context available")
        val result = toolRegistry().execute(name, args, toolContext)
            ?: return errorJson(id, -32601, "unknown tool: $name")
        if (!result.success) {
            return result.toMcpErrorJson(id).let { gson.toJson(it) }
        }
        return resultJson(id, result.toMcpJson())
    }

    private fun initializeResult(id: JsonElement, request: JsonObject): String = resultJson(id, JsonObject().apply {
        val negotiatedProtocol = request.getAsJsonObject("params")
            ?.get("protocolVersion")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.takeIf { it.isNotBlank() }
            ?: "2025-03-26"
        addProperty("protocolVersion", negotiatedProtocol)
        add("capabilities", JsonObject().apply { add("tools", JsonObject()) })
        add("serverInfo", JsonObject().apply {
            addProperty("name", "xed-ide-bridge")
            addProperty("version", "1.0.0")
            addProperty("instructions", "Call the 'getGuidelines' tool to learn about high-performance workflow patterns for this IDE.")
        })
    })

    private fun toolsListResult(id: JsonElement): String =
        resultJson(id, JsonObject().apply { add("tools", toolRegistry().listSchemas()) })

    fun resultJson(id: JsonElement?, result: JsonObject): String =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id ?: JsonNull.INSTANCE)
            add("result", result)
        }.let { gson.toJson(it) }

    fun errorJson(id: JsonElement?, code: Int, message: String): String =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id ?: JsonNull.INSTANCE)
            add("error", JsonObject().apply {
                addProperty("code", code)
                addProperty("message", message)
            })
        }.let { gson.toJson(it) }

    fun notificationJson(method: String, params: JsonObject): String =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", method)
            add("params", params)
        }.let { gson.toJson(it) }
}
