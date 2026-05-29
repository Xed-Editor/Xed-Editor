package com.rk.ai.bridge.server

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolRegistry
import com.rk.ai.bridge.McpToolResult
import com.rk.ai.bridge.tools.ToolError
import com.rk.ai.service.IdeService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class McpDispatcher(
    private val toolRegistry: () -> McpToolRegistry,
    private val ideService: IdeService,
    private val serverScope: CoroutineScope,
) {
    @Volatile var sendNotification: (String, String, JsonObject) -> Unit = { _, _, _ -> }
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val _streamingResponses = MutableSharedFlow<Pair<String, JsonObject>>(extraBufferCapacity = 128)
    val streamingResponses: Flow<Pair<String, JsonObject>> = _streamingResponses.asSharedFlow()

    suspend fun dispatch(sessionId: String, id: JsonElement, method: String, request: JsonObject): String = when (method) {
        "initialize" -> initializeResult(id, request)
        "tools/list" -> toolsListResult(id)
        "tools/call" -> toolsCallResult(sessionId, id, request)
        "notifications/initialized" -> resultJson(id, JsonObject())
        "ping" -> resultJson(id, JsonObject())
        else -> errorJson(id, -32601, "method not found: $method")
    }

    private fun initializeResult(id: JsonElement, request: JsonObject): String = resultJson(id, JsonObject().apply {
        val negotiatedProtocol = request.getAsJsonObject("params")
            ?.get("protocolVersion")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.takeIf { it.isNotBlank() }
            ?: "2025-03-26"
        addProperty("protocolVersion", negotiatedProtocol)
        add("capabilities", JsonObject().apply {
            add("tools", JsonObject())
        })
        add("serverInfo", JsonObject().apply {
            addProperty("name", "xed-ide-bridge")
            addProperty("version", "2.1.0")
        })
        addProperty("instructions", "Call the 'getGuidelines' tool to learn about high-performance workflow patterns for this IDE.")
    })

    private fun toolsListResult(id: JsonElement): String =
        resultJson(id, JsonObject().apply { add("tools", toolRegistry().listSchemas()) })

    private suspend fun toolsCallResult(sessionId: String, id: JsonElement, request: JsonObject): String {
        val params = request.getAsJsonObject("params") ?: return errorJson(id, -32602, "missing params")
        val name = params.get("name")?.asString.orEmpty()
        val args = params.getAsJsonObject("arguments") ?: JsonObject()
        val timeoutMs = toolRegistry().getTimeoutMs(name)
        return try {
            val result = executeTool(sessionId, name, args, timeoutMs)
            resultJson(id, result.toJson())
        } catch (e: ToolError) {
            errorJson(id, e.code, e.message)
        } catch (e: TimeoutCancellationException) {
            errorJson(id, -32000, "tool '$name' timed out after ${timeoutMs}ms")
        } catch (e: CancellationException) {
            errorJson(id, -32000, "tool '$name' was cancelled")
        } catch (e: Exception) {
            errorJson(id, -32603, "${e::class.java.simpleName}: ${e.message ?: "internal error"}")
        }
    }

    private suspend fun executeTool(sessionId: String, name: String, args: JsonObject, timeoutMs: Long): McpToolResult = coroutineScope {
        val tool = toolRegistry().get(name) ?: return@coroutineScope McpToolResult.error("unknown tool: $name")

        val context = McpToolContext(
            ideService = ideService,
            scope = this,
            timeoutMs = timeoutMs,
        )

        val progressJob = launch {
            context.progress.collect { message ->
                sendNotification(sessionId, "tool/progress", JsonObject().apply {
                    addProperty("tool", name)
                    addProperty("message", message)
                })
            }
        }

        try {
            withTimeout(timeoutMs) {
                tool.execute(args, context)
            }
        } finally {
            progressJob.cancel()
        }
    }

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
            add("error", JsonObject().apply { addProperty("code", code); addProperty("message", message) })
        }.let { gson.toJson(it) }

    fun notificationJson(method: String, params: JsonObject): String =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", method)
            add("params", params)
        }.let { gson.toJson(it) }
}
