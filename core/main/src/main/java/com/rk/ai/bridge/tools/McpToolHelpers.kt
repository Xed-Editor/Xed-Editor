package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.rk.ai.service.IdeService
import java.io.File

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

fun jsonResult(data: JsonElement): JsonObject {
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

suspend fun showPatchAndApply(
    ideService: IdeService,
    file: File,
    newContent: String,
    title: String = "Review file change",
    refreshAfterApply: Boolean = true,
): String {
    val oldContent = ideService.getFileContent(file.absolutePath)
        ?: runCatching { file.readText() }.getOrDefault("")
    ideService.showPatch(file.absolutePath, oldContent, newContent, title) {
        ideService.writeFile(file, newContent)
        if (refreshAfterApply) ideService.refreshEditors(force = false)
    }
    return "Review opened in Xed Editor for ${file.absolutePath}. Results will be sent via notifications."
}
