package com.rk.ai.bridge.stitch

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ExternalMcpToolSchema(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val serverName: String,
)

data class ExternalMcpCallResult(
    val success: Boolean,
    val output: String,
    val error: String = "",
    val durationMs: Long = 0L,
)

class ExternalMcpClient(
    val serverName: String,
    val baseUrl: String,
    private val apiKey: String?,
    val timeoutMs: Long = 60_000L,
) {
    private val gson = GsonBuilder().create()
    private val requestIdCounter = ConcurrentHashMap<String, Int>()

    private fun nextId(): String {
        val prefix = "xed-stitch-"
        val counter = requestIdCounter.merge(prefix, 1) { _, old -> old + 1 }
        return "$prefix$counter-${UUID.randomUUID().toString().take(8)}"
    }

    suspend fun listTools(): List<ExternalMcpToolSchema> {
        val requestId = nextId()
        val request = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", requestId)
            addProperty("method", "tools/list")
            add("params", JsonObject())
        }
        val response = sendRequest(request) ?: return emptyList()
        val result = response.getAsJsonObject("result")
            ?: return emptyList()
        val tools = result.getAsJsonArray("tools") ?: JsonArray()
        return tools.mapNotNull { el ->
            val obj = el.asJsonObject
            val name = obj.get("name")?.asString ?: return@mapNotNull null
            val description = obj.get("description")?.asString ?: ""
            val inputSchema = obj.getAsJsonObject("inputSchema") ?: JsonObject()
            ExternalMcpToolSchema(name, description, inputSchema, serverName)
        }
    }

    suspend fun callTool(toolName: String, arguments: JsonObject): ExternalMcpCallResult {
        val start = System.currentTimeMillis()
        val requestId = nextId()
        val request = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", requestId)
            addProperty("method", "tools/call")
            add("params", JsonObject().apply {
                addProperty("name", toolName)
                add("arguments", arguments)
            })
        }
        val response = sendRequest(request)
        val duration = System.currentTimeMillis() - start
        if (response == null) {
            return ExternalMcpCallResult(
                success = false,
                output = "",
                error = "No response from MCP server '$serverName' at $baseUrl",
                durationMs = duration,
            )
        }
        val error = response.getAsJsonObject("error")
        if (error != null) {
            val msg = error.get("message")?.asString ?: "unknown error"
            return ExternalMcpCallResult(success = false, output = "", error = msg, durationMs = duration)
        }
        val result = response.getAsJsonObject("result") ?: JsonObject()
        val content = result.getAsJsonArray("content") ?: JsonArray()
        val text = content.mapNotNull { el ->
            val obj = el.asJsonObject
            if (obj.get("type")?.asString == "text") obj.get("text")?.asString else null
        }.joinToString("\n")
        val isError = result.get("isError")?.asBoolean ?: false
        return ExternalMcpCallResult(
            success = !isError,
            output = text,
            error = if (isError) text else "",
            durationMs = duration,
        )
    }

    private fun sendRequest(request: JsonObject): JsonObject? {
        return runCatching {
            val endpoint = if (baseUrl.endsWith("/mcp")) baseUrl else "$baseUrl/mcp"
            val url = URI(endpoint).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = (timeoutMs / 2).coerceAtMost(30_000).toInt()
            conn.readTimeout = timeoutMs.toInt()
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            if (apiKey != null) {
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            val body = gson.toJson(request)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val responseCode = conn.responseCode
            val responseStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseBytes = responseStream?.let { stream ->
                ByteArrayOutputStream().use { buf ->
                    stream.copyTo(buf)
                    buf.toByteArray()
                }
            } ?: return@runCatching null

            val responseStr = String(responseBytes, Charsets.UTF_8)
            JsonParser.parseString(responseStr).asJsonObject
        }.getOrNull()
    }

    fun isReachable(): Boolean {
        return runCatching {
            val url = URI(baseUrl).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.responseCode in 200..399
        }.getOrDefault(false)
    }
}
