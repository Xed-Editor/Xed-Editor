package com.rk.ai.bridge.server

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolRegistry
import com.rk.ai.bridge.tools.ToolError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class McpDispatcher(private val toolRegistry: () -> McpToolRegistry) {

    private val gson = GsonBuilder().disableHtmlEscaping().create()

    fun dispatch(id: JsonElement, method: String, request: JsonObject): String = when (method) {
        "initialize" -> initializeResult(id, request)
        "tools/list" -> toolsListResult(id)
        "tools/call" -> toolsCallResult(id, request)
        "notifications/initialized" -> resultJson(id, JsonObject())
        "notifications/cancelled" -> resultJson(id, JsonObject())
        "ping" -> resultJson(id, JsonObject().apply { addProperty("pong", true) })
        else -> errorJson(id, -32601, "method not found: $method")
    }

    private fun initializeResult(id: JsonElement, request: JsonObject): String = resultJson(id, JsonObject().apply {
        val negotiatedProtocol = request.getAsJsonObject("params")
            ?.get("protocolVersion")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.takeIf { it.isNotBlank() }
            ?: "2024-11-05"
        addProperty("protocolVersion", negotiatedProtocol)
        add("capabilities", JsonObject().apply {
            add("tools", JsonObject())
            add("experimental", JsonObject().apply {
                addProperty("streaming", true)
            })
        })
        add("serverInfo", JsonObject().apply {
            addProperty("name", "xed-ide-bridge")
            addProperty("version", "1.0.0")
        })
        addProperty("instructions", "Call the 'getGuidelines' tool to learn about high-performance workflow patterns for this IDE.")
    })

    private fun toolsListResult(id: JsonElement): String =
        resultJson(id, JsonObject().apply { add("tools", toolRegistry().listSchemas()) })

    private fun toolsCallResult(id: JsonElement, request: JsonObject): String {
        val params = request.getAsJsonObject("params") ?: return errorJson(id, -32602, "missing params")
        val name = params.get("name")?.asString.orEmpty()
        val args = params.getAsJsonObject("arguments") ?: JsonObject()
        val timeoutMs = toolRegistry().getTimeoutMs(name)

        if (name.isBlank()) return errorJson(id, -32602, "tool name required")
        if (!toolRegistry().listNames().contains(name)) {
            val available = toolRegistry().listNames().sorted().joinToString(", ")
            return errorJson(id, -32601, "unknown tool: '$name'. Available: $available")
        }

        return try {
            val result = runBlocking(Dispatchers.Default) {
                withTimeout(timeoutMs) { toolRegistry().execute(name, args) }
            } ?: return errorJson(id, -32601, "unknown tool: '$name'")
            resultJson(id, result)
        } catch (e: ToolError) {
            errorJson(id, e.code, e.message)
        } catch (e: TimeoutCancellationException) {
            errorJson(id, -32000, "tool '$name' timed out after ${timeoutMs}ms")
        } catch (e: Exception) {
            val cause = e.cause
            when {
                cause is ToolError -> errorJson(id, cause.code, cause.message)
                cause is TimeoutCancellationException -> errorJson(id, -32000, "tool '$name' timed out after ${timeoutMs}ms")
                else -> errorJson(id, -32603, "${e::class.java.simpleName}: ${e.message ?: "internal error"}")
            }
        }
    }

    fun resultJson(id: JsonElement?, result: JsonObject): String =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            if (id != null && id !== JsonNull.INSTANCE) add("id", id)
            add("result", result)
        }.let { gson.toJson(it) }

    fun errorJson(id: JsonElement?, code: Int, message: String): String =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id ?: JsonNull.INSTANCE)
            add("error", JsonObject().apply { addProperty("code", code); addProperty("message", message) })
        }.let { gson.toJson(it) }

    fun notificationJson(method: String, params: JsonObject): String =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", method)
            add("params", params)
        }.let { gson.toJson(it) }
}
