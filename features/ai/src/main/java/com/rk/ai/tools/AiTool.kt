package com.rk.ai.tools

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class AiToolKind {
    Read,

    Write,

    Shell,

    Network,
}

class AiTool(
    val name: String,
    val description: String,
    val kind: AiToolKind,
    val parameters: List<AiToolParameter> = emptyList(),
    val isDestructive: Boolean = kind != AiToolKind.Read,
    val targetPath: ((JsonObject) -> String?)? = null,
    val preview: (suspend (JsonObject) -> String?)? = null,
    val execute: suspend (JsonObject) -> String,
)

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

internal fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.requireString(name: String): String =
    string(name) ?: throw IllegalArgumentException("Missing required argument '$name'")

internal fun JsonObject.int(name: String): Int? = string(name)?.trim()?.toIntOrNull()

internal fun JsonObject.boolean(name: String): Boolean? = string(name)?.trim()?.toBooleanStrictOrNull()
