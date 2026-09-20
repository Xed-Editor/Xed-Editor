package com.rk.ai.tools

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** What a tool does to the world; the permission gate ([com.rk.ai.chat.gateFor]) keys off this. */
enum class AiToolKind {
    Read,

    Write,

    Shell,

    Network,
}

/**
 * A single capability offered to the model. Exactly one of [execute] and [handler] must be set:
 * [execute] runs on [kotlinx.coroutines.Dispatchers.IO], [handler] on the agent's own dispatcher so it
 * can touch UI state.
 *
 * @param targetPath the argument naming the file the call touches, the key for "always allow this file".
 */
class AiTool(
    val name: String,
    val description: String,
    val kind: AiToolKind,
    val parameters: List<AiToolParameter> = emptyList(),
    val isDestructive: Boolean = kind != AiToolKind.Read,
    val targetPath: ((JsonObject) -> String?)? = null,
    val preview: (suspend (JsonObject) -> String?)? = null,
    val presenter: AiToolPresenter? = null,
    val mainAgentOnly: Boolean = false,
    val handler: (suspend (JsonObject, AiToolSession) -> String)? = null,
    val execute: (suspend (JsonObject) -> String)? = null,
) {
    init {
        require(execute != null || handler != null) { "Tool '$name' must define execute or handler" }
        require(!(execute != null && handler != null)) {
            "Tool '$name' must define either execute or handler, not both"
        }
    }
}

class AiToolParameter(
    val name: String,
    val description: String,
    val type: ToolParameterType,
    val required: Boolean = true,
)

fun AiTool.toDescriptor(): ToolDescriptor =
    ToolDescriptor(
        name = name,
        description = description,
        requiredParameters = parameters.filter { it.required }.map { it.toParameterDescriptor() },
        optionalParameters = parameters.filterNot { it.required }.map { it.toParameterDescriptor() },
    )

private fun AiToolParameter.toParameterDescriptor(): ToolParameterDescriptor =
    ToolParameterDescriptor(name = name, description = description, type = type)

/** Tolerates the model sending numbers and booleans as strings. */
fun JsonObject.arg(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

fun JsonObject.displayArg(name: String): String? = arg(name)?.takeIf { it.isNotBlank() }

fun JsonObject.requireArg(name: String): String =
    arg(name) ?: throw IllegalArgumentException("Missing required argument '$name'")

fun JsonObject.argInt(name: String): Int? = arg(name)?.trim()?.toIntOrNull()

fun JsonObject.argBoolean(name: String): Boolean? = arg(name)?.trim()?.toBooleanStrictOrNull()
