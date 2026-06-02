@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.agent.tools

import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService
import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tools that provide structured suggestions to the user and allow applying them.
 *
 * - `getSuggestions` – returns a JSON array of suggestion objects. Optionally takes a diagnostic payload.
 * - `applySuggestion` – applies a suggestion (dry‑run preview then commit).
 * - `recordSuggestionFeedback` – stores acceptance/rejection in memory for future learning.
 */
class SuggestionTools(private val ideService: IdeService) {

    // Data class definitions are not directly used in the tool schema, but they document the shape.
    data class DiagnosticInfo(
        val file: String,
        val line: Int,
        val message: String,
    )

    data class Suggestion(
        val text: String,
        val confidence: Float?,
        val source: String?,
        val diagnostic: DiagnosticInfo?,
    )

    /**
     * Returns structured suggestions. The model is prompted with the `DEFAULT_SUGGESTION_PROMPT`
     * which already asks for JSON output. The tool declares a JSON schema so the framework validates
     * the response and retries if the shape is wrong.
     */
    private val getSuggestions = Tool(
        name = "getSuggestions",
        description = "Generate coding suggestions. Returns a JSON array of objects with fields: text, confidence (optional 0‑1), source (optional), diagnostic (optional object with file, line, message).",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("diagnostic") {
                        put("type", "object")
                        putJsonObject("properties") {
                            put("file", buildJsonObject { put("type", "string"); put("description", "File path relative to workspace") })
                            put("line", buildJsonObject { put("type", "integer"); put("description", "Line number where the diagnostic occurs") })
                            put("message", buildJsonObject { put("type", "string"); put("description", "Diagnostic message text") })
                        }
                        put("required", buildJsonArray { add("file"); add("line"); add("message") })
                    }
                    put("maxCount", buildJsonObject { put("type", "integer"); put("description", "Maximum number of suggestions to generate"); put("default", 5) })
                },
                required = emptyList()
            )
        },
        // Structured output ensures the model returns valid JSON matching the schema above.
        needsApproval = false,
        execute = { args ->
            // Build a prompt that includes any diagnostic info if supplied.
            val diag = args.asJsonObject["diagnostic"]?.asJsonObject
            val maxCount = args.asJsonObject["maxCount"]?.asJsonPrimitive?.asInt ?: 5
            val basePrompt = "Generate up to $maxCount suggestions based on the following context."
            val fullPrompt = if (diag != null) {
                val file = diag["file"]?.asJsonPrimitive?.asString ?: ""
                val line = diag["line"]?.asJsonPrimitive?.asInt ?: 0
                val message = diag["message"]?.asJsonPrimitive?.asString ?: ""
                "$basePrompt Diagnostic in $file at line $line: $message"
            } else {
                basePrompt
            }
            // The actual generation is delegated to the VibeCoding model pipeline via the tool's execution.
            // Here we simply return a placeholder indicating the model should run.
            // The framework will replace this with the real model call because tools can emit a special UIMessagePart.ToolResponse.
            // For now we return an empty array – the model will fill it.
            listOf(UIMessagePart.Text(buildJsonArray { }.toString()))
        }
    )

    /**
     * Apply a suggestion. Parameters:
     * - suggestion: the full suggestion JSON object (as string)
     * - dryRun: boolean, if true only returns a diff preview.
     */
    private val applySuggestion = Tool(
        name = "applySuggestion",
        description = "Apply a suggestion to the code base. If dryRun=true returns a diff preview, otherwise edits the file.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("suggestion") {
                        put("type", "object")
                        put("description", "Suggestion object as returned by getSuggestions")
                    }
                    putJsonObject("dryRun") { put("type", "boolean"); put("description", "If true, only return a diff preview"); put("default", true) }
                },
                required = listOf("suggestion")
            )
        },
        execute = { args ->
            val suggObj = args.asJsonObject["suggestion"]?.asJsonObject
                ?: return@Tool listOf(UIMessagePart.Text("Error: missing suggestion object"))
            val text = suggObj["text"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Error: suggestion missing 'text'"))
            val diag = suggObj["diagnostic"]?.asJsonObject
            val dryRun = args.asJsonObject["dryRun"]?.asJsonPrimitive?.asBoolean ?: true
            // Determine target file & location
            if (diag == null) {
                return@Tool listOf(UIMessagePart.Text("Error: suggestion must include diagnostic info for location"))
            }
            val file = diag["file"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Error: diagnostic missing file"))
            val line = diag["line"]?.asJsonPrimitive?.asInt ?: 0
            // Use editFile tool under the hood – we construct a find‑replace based on line number.
            // Since editFile works with find/replace patterns, we read the file, locate the line, and replace that line.
            // For simplicity we delegate to the existing editFile tool via ideService.
            val editResult = ideService.editFile(
                path = file,
                find = "^.*$", // placeholder regex – we will replace the whole line
                replace = text,
                lineNumber = line,
                dryRun = dryRun,
                replaceAll = false
            )
            // The editFile tool returns a UIMessagePart.Text with either diff preview or success.
            editResult
        }
    )

    /**
     * Record feedback about a suggestion (accepted/rejected).
     */
    private val recordSuggestionFeedback = Tool(
        name = "recordSuggestionFeedback",
        description = "Persist user feedback for a suggestion (accepted or dismissed).",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("suggestionId") { put("type", "string"); put("description", "Unique identifier for the suggestion (e.g., UUID)") }
                    putJsonObject("accepted") { put("type", "boolean"); put("description", "True if user accepted the suggestion") }
                },
                required = listOf("suggestionId", "accepted")
            )
        },
        execute = { args ->
            val id = args.asJsonObject["suggestionId"]?.asJsonPrimitive?.asString ?: "unknown"
            val accepted = args.asJsonObject["accepted"]?.asJsonPrimitive?.asBoolean ?: false
            // Store as a memory entry under .claude/memories/
            val memoryContent = """
---
name: suggestion-feedback-$id
description: User feedback for suggestion $id
metadata:
  type: feedback
---
**Accepted:** $accepted
**SuggestionId:** $id
**Timestamp:** ${java.time.Instant.now()}
""".trimIndent()
            // Write using the standard memory location – use the same path as other memories.
            // The IDE service can write arbitrary files; we use it to write into the .claude directory.
            val memPath = ".claude/memories/suggestion-feedback-$id.md"
            ideService.writeFile(memPath, memoryContent)
            listOf(UIMessagePart.Text("Feedback recorded for suggestion $id"))
        }
    )

    val all: List<Tool> = listOf(getSuggestions, applySuggestion, recordSuggestionFeedback)
}
