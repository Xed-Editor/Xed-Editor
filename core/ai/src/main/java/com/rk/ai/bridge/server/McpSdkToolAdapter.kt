package com.rk.ai.bridge.server

import com.google.gson.JsonPrimitive as GsonPrimitive
import com.google.gson.JsonNull as GsonNull
import com.google.gson.JsonObject as GsonObject
import com.google.gson.JsonArray as GsonArray
import com.google.gson.JsonElement as GsonElement
import com.rk.ai.bridge.McpTool
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolRegistry
import com.rk.ai.bridge.McpToolResult
import com.rk.ai.service.IdeService
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun McpTool.toSdkToolSchema(): ToolSchema {
    val props = buildJsonObject {
        getRequiredParams().forEach { (name, type) ->
            put(name, buildJsonObject {
                put("type", JsonPrimitive(type))
                getRequiredParamDescriptions()[name]?.let {
                    put("description", JsonPrimitive(it))
                }
            })
        }
        getOptionalParams().forEach { (name, type) ->
            put(name, buildJsonObject {
                put("type", JsonPrimitive(type))
                getOptionalParamDescriptions()[name]?.let {
                    put("description", JsonPrimitive(it))
                }
            })
        }
    }
    val required = getRequiredParams().keys.toList()
    return ToolSchema(
        properties = props.takeIf { it.entries.isNotEmpty() },
        required = required.takeIf { it.isNotEmpty() },
    )
}

internal fun JsonElement.toGson(): GsonElement = when (this) {
    is JsonPrimitive -> when {
        isString -> GsonPrimitive(content)
        content == "true" || content == "false" -> GsonPrimitive(content.toBoolean())
        else -> {
            content.toLongOrNull()?.let { GsonPrimitive(it) }
                ?: content.toDoubleOrNull()?.let { GsonPrimitive(it) }
                ?: GsonPrimitive(content)
        }
    }
    is JsonNull -> GsonNull.INSTANCE
    is JsonObject -> {
        val obj = GsonObject()
        for ((key, value) in this) {
            obj.add(key, value.toGson())
        }
        obj
    }
    is JsonArray -> {
        val arr = GsonArray()
        for (item in this) {
            arr.add(item.toGson())
        }
        arr
    }
}

internal fun McpToolResult.toCallToolResult(): CallToolResult {
    return if (success) {
        CallToolResult(content = listOf(TextContent(text = output)))
    } else {
        CallToolResult(
            content = listOf(TextContent(text = error.ifBlank { output.ifBlank { "tool failed" } })),
            isError = true,
        )
    }
}

internal suspend fun executeTool(
    tool: McpTool,
    args: GsonObject,
    ideService: IdeService,
    scope: CoroutineScope,
    onProgress: (String) -> Unit,
): McpToolResult {
    val context = McpToolContext(
        ideService = ideService,
        scope = scope,
        timeoutMs = tool.getTimeoutMs(),
    )
    val progressJob = scope.launch {
        context.progress.collect { message ->
            onProgress(message)
        }
    }
    try {
        return withTimeout(tool.getTimeoutMs()) {
            tool.execute(args, context)
        }
    } catch (e: TimeoutCancellationException) {
        return McpToolResult.error("tool '${tool.getName()}' timed out after ${tool.getTimeoutMs()}ms")
    } catch (e: Exception) {
        return McpToolResult.error("${e::class.java.simpleName}: ${e.message ?: "internal error"}")
    } finally {
        progressJob.cancel()
    }
}

internal fun registerToolsToSdkServer(
    sdkServer: io.modelcontextprotocol.kotlin.sdk.server.Server,
    registry: McpToolRegistry,
    ideServiceProvider: () -> IdeService,
    progressCallback: (toolName: String, message: String) -> Unit,
) {
    for (name in registry.listNames()) {
        val tool = registry.get(name) ?: continue
        sdkServer.addTool(
            name = tool.getName(),
            description = tool.getDescription(),
            inputSchema = tool.toSdkToolSchema(),
        ) { request ->
            val gsonArgs = (request.arguments?.toGson() as? GsonObject) ?: GsonObject()
            val result = coroutineScope {
                executeTool(
                    tool = tool,
                    args = gsonArgs,
                    ideService = ideServiceProvider(),
                    scope = this,
                    onProgress = { msg -> progressCallback(tool.getName(), msg) },
                )
            }
            if (result.success) {
                CallToolResult(content = listOf(TextContent(text = result.output)))
            } else {
                CallToolResult(
                    content = listOf(TextContent(text = result.error.ifBlank { result.output.ifBlank { "tool failed" } })),
                    isError = true,
                )
            }
        }
    }
}
