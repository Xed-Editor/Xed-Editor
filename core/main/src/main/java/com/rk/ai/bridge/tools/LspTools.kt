package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService

class GetDiagnosticsTool : McpTool {
    override fun getName(): String = "getDiagnostics"
    override fun getDescription(): String = "Returns LSP diagnostics for a file."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        val results = ideService.getDiagnostics(filePath)
        return JsonObject().apply {
            add("content", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", results.toString())
                })
            })
        }
    }
}

class FindDefinitionsTool : McpTool {
    override fun getName(): String = "findDefinitions"
    override fun getDescription(): String = "Finds the definition location of a symbol."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "line" to "number", "column" to "number")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        val line = args.get("line")?.asInt ?: throw IllegalArgumentException("line required")
        val column = args.get("column")?.asInt ?: throw IllegalArgumentException("column required")
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        val results = ideService.findDefinitions(filePath, line, column)
        return JsonObject().apply {
            add("content", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", results.toString())
                })
            })
        }
    }
}

class FindReferencesTool : McpTool {
    override fun getName(): String = "findReferences"
    override fun getDescription(): String = "Finds all references to a symbol."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "line" to "number", "column" to "number")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        val line = args.get("line")?.asInt ?: throw IllegalArgumentException("line required")
        val column = args.get("column")?.asInt ?: throw IllegalArgumentException("column required")
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        val results = ideService.findReferences(filePath, line, column)
        return JsonObject().apply {
            add("content", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", results.toString())
                })
            })
        }
    }
}
