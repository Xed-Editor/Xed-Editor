package com.rk.ai.bridge

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.service.IdeService
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class McpToolResult(
    val success: Boolean,
    val text: String? = null,
    val data: JsonObject? = null,
    val error: String? = null,
    val errorCode: Int? = null,
    val durationMs: Long = 0L,
) {
    companion object {
        fun text(text: String, durationMs: Long = 0L) = McpToolResult(true, text = text, durationMs = durationMs)
        fun json(data: JsonObject, durationMs: Long = 0L) = McpToolResult(true, text = data.toString(), data = data, durationMs = durationMs)
        fun error(message: String, code: Int = -32603) = McpToolResult(false, error = message, errorCode = code)
        fun streamed(text: String, durationMs: Long = 0L) = McpToolResult(true, text = text, durationMs = durationMs)
    }

    fun toMcpJson(): JsonObject = JsonObject().apply {
        add("content", JsonArray().apply {
            val displayText = text ?: data?.toString() ?: error ?: ""
            if (displayText.isNotBlank()) {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", displayText)
                })
            }
        })
        addProperty("_success", success)
        if (durationMs > 0) addProperty("_duration_ms", durationMs)
        if (error != null) {
            addProperty("isError", true)
            addProperty("_error", error)
            if (errorCode != null) addProperty("_error_code", errorCode)
        }
    }

    fun toMcpErrorJson(id: com.google.gson.JsonElement?): JsonObject = JsonObject().apply {
        addProperty("jsonrpc", "2.0")
        add("id", id ?: com.google.gson.JsonNull.INSTANCE)
        add("error", JsonObject().apply {
            addProperty("code", errorCode ?: -32603)
            addProperty("message", error ?: "unknown error")
        })
    }
}

class McpToolContext(
    val ideService: IdeService,
    private val sseManager: com.rk.ai.bridge.IdeNotificationSender?,
    private val sessionId: String?,
) {
    private val startTime = System.currentTimeMillis()

    fun elapsedMs(): Long = System.currentTimeMillis() - startTime

    fun pushProgress(progress: Float, message: String) {
        sseManager?.sendNotification("tool/progress", JsonObject().apply {
            addProperty("progress", progress.coerceIn(0f, 1f))
            addProperty("message", message)
        })
    }

    fun pushStream(chunk: String) {
        sseManager?.sendNotification("tool/stream", JsonObject().apply {
            addProperty("data", chunk)
            addProperty("sessionId", sessionId ?: "default")
        })
    }
}

interface McpTool {
    val name: String
    val description: String
    val timeoutMs: Long get() = 60_000L
    val requiredParams: Map<String, String> get() = emptyMap()
    val optionalParams: Map<String, String> get() = emptyMap()

    suspend fun execute(args: JsonObject, context: McpToolContext): McpToolResult

    fun getSchema(): JsonObject = JsonObject().apply {
        addProperty("name", name)
        addProperty("description", description)
        add("inputSchema", JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                requiredParams.forEach { (k, t) -> add(k, JsonObject().apply { addProperty("type", t) }) }
                optionalParams.forEach { (k, t) -> add(k, JsonObject().apply { addProperty("type", t) }) }
            })
            add("required", JsonArray().apply { requiredParams.keys.forEach { add(it) } })
        })
    }
}

class McpToolRegistry(private val ideService: IdeService) {
    private val tools = mutableMapOf<String, McpTool>()

    fun register(tool: McpTool) { tools[tool.name] = tool }

    suspend fun execute(name: String, args: JsonObject, context: McpToolContext): McpToolResult? {
        val tool = tools[name] ?: return null
        val start = System.currentTimeMillis()
        return try {
            val result = withTimeout(tool.timeoutMs) { tool.execute(args, context) }
            result.copy(durationMs = result.durationMs.coerceAtLeast(System.currentTimeMillis() - start))
        } catch (e: TimeoutCancellationException) {
            McpToolResult.error("Tool '$name' timed out after ${tool.timeoutMs}ms", -32000)
        } catch (e: Exception) {
            McpToolResult.error("${e::class.java.simpleName}: ${e.message ?: "internal error"}")
        }
    }

    fun listSchemas(): JsonArray = JsonArray().apply { tools.values.forEach { add(it.getSchema()) } }
    fun getTool(name: String): McpTool? = tools[name]
    val toolCount: Int get() = tools.size
}
