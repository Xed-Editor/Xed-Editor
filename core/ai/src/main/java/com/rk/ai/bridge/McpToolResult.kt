package com.rk.ai.bridge

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class McpToolResult(
    val success: Boolean = true,
    val output: String = "",
    val error: String = "",
    val metadata: Map<String, Any> = emptyMap(),
    val durationMs: Long = 0L,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        add("content", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", output.ifEmpty { if (error.isNotEmpty()) error else "(empty)" })
                if (metadata.isNotEmpty()) {
                    add("metadata", JsonObject().apply {
                        metadata.forEach { (k, v) ->
                            when (v) {
                                is String -> addProperty(k, v)
                                is Number -> addProperty(k, v)
                                is Boolean -> addProperty(k, v)
                                else -> addProperty(k, v.toString())
                            }
                        }
                    })
                }
                addProperty("durationMs", durationMs)
            })
        })
    }

    fun toErrorJson(): JsonObject = JsonObject().apply {
        add("content", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", error)
                addProperty("isError", true)
            })
        })
    }

    companion object {
        fun success(text: String, meta: Map<String, Any> = emptyMap(), duration: Long = 0L): McpToolResult =
            McpToolResult(success = true, output = text, metadata = meta, durationMs = duration)

        fun error(msg: String, meta: Map<String, Any> = emptyMap(), duration: Long = 0L): McpToolResult =
            McpToolResult(success = false, error = msg, metadata = meta, durationMs = duration)
    }
}

fun McpToolResult.toJsonElement(): JsonElement = if (success) toJson() else toErrorJson()

fun textResult(text: String): JsonObject = McpToolResult.success(text).toJson()

fun jsonResult(data: JsonElement): JsonObject = McpToolResult.success(data.toString()).toJson()
