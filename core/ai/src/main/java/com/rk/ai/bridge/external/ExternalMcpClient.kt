package com.rk.ai.bridge.external

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

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
    private val headers: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 60_000L,
) {
    companion object {
        private const val TAG = "ExternalMcpClient"
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 30_000L
    }

    private val gson = GsonBuilder().create()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutMs.coerceAtMost(30_000), TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .followSslRedirects(true)
        .followRedirects(true)
        .build()

    private val httpClient = HttpClient(OkHttp) {
        engine { preconfigured = okHttpClient }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    @Volatile
    private var mcpClient: Client? = null

    @Volatile
    private var consecutiveFailures: Int = 0

    private val mcpJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val endpointUrl: String
        get() {
            val base = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            return if (base.endsWith("/mcp")) base else "$base/mcp"
        }

    suspend fun ensureConnected() {
        if (mcpClient?.transport == null) {
            connectWithRetry()
        }
    }

    private suspend fun connectWithRetry() {
        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                connect()
                consecutiveFailures = 0
                return
            } catch (e: Exception) {
                lastException = e
                consecutiveFailures++
                val backoff = calculateBackoff(attempt)
                if (com.rk.xededitor.BuildConfig.DEBUG) {
                    Log.w(TAG, "Connection attempt $attempt/$MAX_RETRIES failed for '$serverName': ${e.message}, retrying in ${backoff}ms")
                }
                kotlinx.coroutines.delay(backoff)
            }
        }
        if (com.rk.xededitor.BuildConfig.DEBUG) {
            Log.e(TAG, "All $MAX_RETRIES connection attempts failed for '$serverName'", lastException)
        }
        throw lastException ?: IllegalStateException("Connection failed")
    }

    private fun calculateBackoff(attempt: Int): Long {
        val backoff = INITIAL_BACKOFF_MS * (1L shl (attempt - 1))
        return min(backoff, MAX_BACKOFF_MS)
    }

    private suspend fun connect() {
        try {
            val transport = StreamableHttpClientTransport(
                url = endpointUrl,
                client = httpClient,
                requestBuilder = {
                    if (apiKey != null) {
                        headers.append("Authorization", "Bearer $apiKey")
                    }
                    this@ExternalMcpClient.headers.forEach { (k, v) ->
                        headers.append(k, v)
                    }
                },
            )
            val client = Client(
                clientInfo = Implementation(
                    name = "xed-mcp-client",
                    version = "1.0.0",
                ),
            )
            client.connect(transport)
            mcpClient = client
            if (com.rk.xededitor.BuildConfig.DEBUG) {
                Log.d(TAG, "Connected to external MCP server '$serverName' at $endpointUrl")
            }
        } catch (e: Exception) {
            mcpClient?.let { runCatching { runBlocking { it.close() } } }
            mcpClient = null
            throw e
        }
    }

    suspend fun listTools(): List<ExternalMcpToolSchema> {
        ensureConnected()
        val client = mcpClient ?: return emptyList()
        return runCatching {
            val result = client.listTools()
            result.tools.map { tool ->
                ExternalMcpToolSchema(
                    name = tool.name,
                    description = tool.description ?: "",
                    inputSchema = parseInputSchema(tool.inputSchema),
                    serverName = serverName,
                )
            }
        }.getOrElse { e ->
            if (com.rk.xededitor.BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to list tools from '$serverName': ${e.message}")
            }
            handleConnectionError(e)
            emptyList()
        }
    }

    suspend fun callTool(toolName: String, arguments: JsonObject): ExternalMcpCallResult {
        val start = System.currentTimeMillis()
        ensureConnected()
        val client = mcpClient
        if (client == null) {
            return ExternalMcpCallResult(
                success = false, output = "",
                error = "No MCP session for server '$serverName' at $baseUrl",
                durationMs = System.currentTimeMillis() - start,
            )
        }
        return runCatching {
            val kotlinxArgs = gsonToKotlinx(arguments)
            val result = withTimeout(timeoutMs) {
                client.callTool(
                    request = CallToolRequest(
                        params = CallToolRequestParams(
                            name = toolName,
                            arguments = kotlinxArgs,
                        ),
                    ),
                    options = RequestOptions(timeout = timeoutMs.seconds),
                )
            }
            val text = result.content.mapNotNull { content ->
                when (content) {
                    is TextContent -> content.text
                    is ImageContent -> "[Image: ${content.mimeType}]"
                    else -> null
                }
            }.joinToString("\n")
            val duration = System.currentTimeMillis() - start
            consecutiveFailures = 0
            ExternalMcpCallResult(
                success = result.isError != true,
                output = text,
                error = if (result.isError == true) text else "",
                durationMs = duration,
            )
        }.getOrElse { e ->
            val duration = System.currentTimeMillis() - start
            if (com.rk.xededitor.BuildConfig.DEBUG) {
                Log.w(TAG, "Tool call '$toolName' failed on '$serverName': ${e.message}")
            }
            handleConnectionError(e)
            ExternalMcpCallResult(
                success = false, output = "",
                error = "Failed to call tool '$toolName' on server '$serverName': ${e.message}",
                durationMs = duration,
            )
        }
    }

    private suspend fun handleConnectionError(e: Throwable) {
        if (isConnectionError(e)) {
            if (com.rk.xededitor.BuildConfig.DEBUG) {
                Log.w(TAG, "Connection error detected for '$serverName', resetting connection")
            }
            mcpClient?.let { runCatching { it.close() } }
            mcpClient = null
        }
    }

    private fun isConnectionError(e: Throwable): Boolean {
        val msg = e.message?.lowercase() ?: return false
        return msg.contains("connection") || msg.contains("closed") ||
            msg.contains("broken pipe") || msg.contains("reset") ||
            msg.contains("eof") || msg.contains("timeout") ||
            e is java.net.ConnectException ||
            e is java.net.SocketException ||
            e is java.net.SocketTimeoutException ||
            e is java.io.IOException
    }

    fun isReachable(): Boolean {
        return runCatching {
            val url = java.net.URI(endpointUrl).toURL()
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            if (apiKey != null) {
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            this@ExternalMcpClient.headers.forEach { (k, v) ->
                conn.setRequestProperty(k, v)
            }
            conn.outputStream.write("{}".toByteArray())
            val code = conn.responseCode
            code in 200..499
        }.getOrDefault(false)
    }

    suspend fun disconnect() {
        runCatching { mcpClient?.close() }
        mcpClient = null
        consecutiveFailures = 0
    }

    private fun parseInputSchema(schema: io.modelcontextprotocol.kotlin.sdk.types.ToolSchema): JsonObject {
        val obj = JsonObject()
        obj.addProperty("type", schema.type)
        val properties = schema.properties
        if (properties != null) {
            obj.add("properties", kotlinxToGson(properties) as? JsonObject ?: JsonObject())
        }
        val required = schema.required
        if (required != null && required.isNotEmpty()) {
            val arr = JsonArray()
            required.forEach { arr.add(it) }
            obj.add("required", arr)
        }
        return obj
    }

    private fun gsonToKotlinx(gson: JsonObject): kotlinx.serialization.json.JsonObject {
        return mcpJson.parseToJsonElement(gson.toString()).jsonObject
    }

    private fun kotlinxToGson(element: kotlinx.serialization.json.JsonElement): com.google.gson.JsonElement {
        return when (element) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                when {
                    element.isString -> com.google.gson.JsonPrimitive(element.content)
                    element.content == "true" || element.content == "false" ->
                        com.google.gson.JsonPrimitive(element.content.toBoolean())
                    else -> {
                        element.content.toLongOrNull()?.let { com.google.gson.JsonPrimitive(it) }
                            ?: element.content.toDoubleOrNull()?.let { com.google.gson.JsonPrimitive(it) }
                            ?: com.google.gson.JsonPrimitive(element.content)
                    }
                }
            }
            is kotlinx.serialization.json.JsonNull -> com.google.gson.JsonNull.INSTANCE
            is kotlinx.serialization.json.JsonObject -> {
                val obj = JsonObject()
                for ((key, value) in element) {
                    obj.add(key, kotlinxToGson(value))
                }
                obj
            }
            is kotlinx.serialization.json.JsonArray -> {
                val arr = JsonArray()
                for (item in element) {
                    arr.add(kotlinxToGson(item))
                }
                arr
            }
        }
    }
}
