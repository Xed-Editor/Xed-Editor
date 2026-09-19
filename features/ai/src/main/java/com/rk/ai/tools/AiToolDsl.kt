package com.rk.ai.tools

import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.JsonObject

/**
 * Small builder for [AiTool] so an extension can declare a tool in one readable block without
 * importing the whole argument model:
 *
 * ```kotlin
 * val now = aiTool("current_time", "Return the current time.", AiToolKind.Read) {
 *     stringParam("format", "java.time format pattern", required = false)
 *     presents { args -> ToolCallView("Clock", args.displayArg("format"), emptyList()) }
 *     executes { args -> LocalTime.now().format(DateTimeFormatter.ofPattern(args.displayArg("format") ?: "HH:mm")) }
 * }
 * ```
 */
fun aiTool(name: String, description: String, kind: AiToolKind, build: AiToolDsl.() -> Unit): AiTool =
    AiToolDsl(name, description, kind).apply(build).build()

class AiToolDsl internal constructor(
    private val name: String,
    private val description: String,
    private val kind: AiToolKind,
) {
    private val parameters = mutableListOf<AiToolParameter>()
    private var isDestructive: Boolean? = null
    private var targetPath: ((JsonObject) -> String?)? = null
    private var preview: (suspend (JsonObject) -> String?)? = null
    private var presenter: AiToolPresenter? = null
    private var mainAgentOnly = false
    private var execute: (suspend (JsonObject) -> String)? = null
    private var handler: (suspend (JsonObject, AiToolSession) -> String)? = null

    /** Declares an argument of any koog parameter type. */
    fun param(name: String, description: String, type: ToolParameterType, required: Boolean = true) {
        parameters += AiToolParameter(name, description, type, required)
    }

    fun stringParam(name: String, description: String, required: Boolean = true) =
        param(name, description, ToolParameterType.String, required)

    fun intParam(name: String, description: String, required: Boolean = true) =
        param(name, description, ToolParameterType.Integer, required)

    fun boolParam(name: String, description: String, required: Boolean = true) =
        param(name, description, ToolParameterType.Boolean, required)

    /** Declares an argument that is only meaningful to your own tool. */
    fun params(vararg parameters: AiToolParameter) {
        this.parameters += parameters
    }

    /** Overrides the default (`kind != Read`). */
    fun destructive(value: Boolean = true) {
        isDestructive = value
    }

    /** Withholds the tool from sub-agents. */
    fun mainAgentOnly(value: Boolean = true) {
        mainAgentOnly = value
    }

    /** The argument that names the file the call touches, used for "allow this file" memory. */
    fun touchesFile(target: (JsonObject) -> String?) {
        targetPath = target
    }

    /** Supplies a unified diff preview shown before the call is approved. */
    fun previewDiff(block: suspend (JsonObject) -> String?) {
        preview = block
    }

    /** Teaches the transcript how to render this tool; omit it for the generic key/value view. */
    fun presents(block: (JsonObject) -> ToolCallView) {
        presenter = AiToolPresenter(block)
    }

    /** A self-contained tool; runs on [kotlinx.coroutines.Dispatchers.IO]. */
    fun executes(block: suspend (JsonObject) -> String) {
        execute = block
    }

    /** A tool that needs the running session (ask the user, sub-agent, goal/task list). */
    fun handles(block: suspend (JsonObject, AiToolSession) -> String) {
        handler = block
    }

    fun build(): AiTool =
        AiTool(
            name = name,
            description = description,
            kind = kind,
            parameters = parameters.toList(),
            isDestructive = isDestructive ?: (kind != AiToolKind.Read),
            targetPath = targetPath,
            preview = preview,
            presenter = presenter,
            mainAgentOnly = mainAgentOnly,
            handler = handler,
            execute = execute,
        )
}
