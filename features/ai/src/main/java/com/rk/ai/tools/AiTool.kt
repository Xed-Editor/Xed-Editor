package com.rk.ai.tools

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * What a tool does to the world. The permission gate ([com.rk.ai.chat.gateFor]) and the settings UI
 * both key off this, so a tool must pick the kind that matches its side effects.
 */
enum class AiToolKind {
    Read,

    Write,

    Shell,

    Network,
}

/**
 * A single capability offered to the model.
 *
 * A tool is described once - schema, UI presentation and behaviour together - so adding one is a
 * single declaration registered with [AiToolRegistry]. Exactly one of [execute] and [handler] must be
 * set:
 *  - [execute] is a self-contained function of its arguments. It runs on [kotlinx.coroutines.Dispatchers.IO].
 *  - [handler] is used by tools that must talk to the running session (ask the user, run a sub-agent,
 *    update the goal). It runs on the agent's own dispatcher so it can touch UI state.
 *
 * @param targetPath the argument that identifies the file the call touches. When present it is the key
 *   used to remember "always allow this file" for the rest of the chat.
 * @param preview an optional unified diff shown to the user before a write is approved.
 * @param presenter how the call is rendered in the transcript. Without one a generic key/value view is
 *   used, which is enough for simple tools.
 * @param mainAgentOnly when true the tool is withheld from sub-agents (they cannot talk to the user or
 *   own the session state).
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

/** A named, typed argument of an [AiTool]. */
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

/**
 * The argument readers a tool (or its presenter) uses.
 *
 * They tolerate the model sending numbers and booleans as strings, which is what most chat
 * completions providers do, and [displayArg] additionally treats a blank value as absent so a
 * presenter can fall back to a placeholder.
 */
fun JsonObject.arg(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

/** Like [arg], but a blank value reads as null - what a transcript label wants. */
fun JsonObject.displayArg(name: String): String? = arg(name)?.takeIf { it.isNotBlank() }

/** Reads a required argument, failing the tool call when the model omitted it. */
fun JsonObject.requireArg(name: String): String =
    arg(name) ?: throw IllegalArgumentException("Missing required argument '$name'")

fun JsonObject.argInt(name: String): Int? = arg(name)?.trim()?.toIntOrNull()

fun JsonObject.argBoolean(name: String): Boolean? = arg(name)?.trim()?.toBooleanStrictOrNull()
