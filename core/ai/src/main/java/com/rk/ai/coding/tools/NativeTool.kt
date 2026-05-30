package com.rk.ai.coding.tools

import com.google.gson.JsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.EditorOps
import com.rk.ai.service.FileOps
import com.rk.ai.service.GitOps
import com.rk.ai.service.IdeService
import com.rk.ai.service.ProjectOps
import com.rk.ai.service.TerminalOps
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

interface NativeTool {
    val name: String
    val description: String
    val permission: ToolPermissionLevel
    val timeoutMs: Long get() = 60_000L
    fun inputSchema(): InputSchema? = null
    suspend fun execute(args: JsonObject, context: NativeToolContext): NativeToolResult
}

data class NativeToolContext(
    val editorOps: EditorOps,
    val fileOps: FileOps,
    val gitOps: GitOps,
    val terminalOps: TerminalOps,
    val projectOps: ProjectOps,
) {
    constructor(ideService: IdeService) : this(
        editorOps = ideService,
        fileOps = ideService,
        gitOps = ideService,
        terminalOps = ideService,
        projectOps = ideService,
    )
}

data class NativeToolResult(
    val success: Boolean,
    val text: String,
) {
    fun toMessageParts(): List<UIMessagePart> =
        listOf(UIMessagePart.Text(if (success) text else """{"error":${text.jsonString()}}"""))

    companion object {
        fun success(text: String): NativeToolResult = NativeToolResult(true, text)
        fun error(text: String): NativeToolResult = NativeToolResult(false, text)
    }
}

class NativeToolError(message: String) : IllegalArgumentException(message)

suspend fun NativeTool.executeWithTimeout(args: JsonObject, context: NativeToolContext): NativeToolResult =
    withTimeout(timeoutMs) {
        runCatching { execute(args, context) }
            .getOrElse { NativeToolResult.error("${it::class.java.simpleName}: ${it.message ?: "tool failed"}") }
    }

internal fun objectSchema(
    properties: Map<String, String>,
    required: List<String> = emptyList(),
): InputSchema = InputSchema.Obj(
    properties = buildJsonObject {
        properties.forEach { (name, type) ->
            putJsonObject(name) { put("type", type) }
        }
    },
    required = required.takeIf { it.isNotEmpty() },
)

internal fun JsonObject.stringParam(vararg names: String): String =
    names.firstNotNullOfOrNull { name ->
        get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
    }.orEmpty()

internal fun JsonObject.optionalStringParam(vararg names: String): String =
    names.firstNotNullOfOrNull { name ->
        get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString
    }.orEmpty()

internal fun JsonObject.intParam(name: String, default: Int? = null): Int? =
    get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asInt ?: default

internal fun JsonObject.booleanParam(name: String, default: Boolean = false): Boolean =
    get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asBoolean ?: default

internal fun String.jsonString(): String = buildString {
    append('"')
    this@jsonString.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
    append('"')
}
