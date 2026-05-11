package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject

fun textResult(text: String): JsonObject {
    return JsonObject().apply {
        add("content", JsonArray().apply {
            if (text.isNotEmpty()) {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", text)
                })
            }
        })
    }
}

fun jsonResult(data: JsonObject): JsonObject {
    return JsonObject().apply {
        add("content", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", data.toString())
            })
        })
    }
}

fun emptyResult(): JsonObject {
    return JsonObject().apply {
        add("content", JsonArray())
    }
}
