@file:OptIn(ExperimentalUuidApi::class)

package com.rk.ai.agent.tools

import com.google.gson.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService

class VibeCodingTerminalTools(private val ideService: IdeService) {

    val all: List<Tool> = listOf(runCommand, getTerminalOutput)

    private val runCommand = Tool(
        name = "runCommand",
        description = "Runs a shell command in the terminal environment. Prefer native IDE tools for file/editor operations, use this only for building, running, testing, or package installs.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("command", "Shell command to execute")
                    putJsonObject("timeoutSeconds") { put("type", "integer"); put("description", "Timeout in seconds (default: 120)") }
                },
                required = listOf("command"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val command = obj["command"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing command"))
            val timeout = obj["timeoutSeconds"]?.asJsonPrimitive?.asLong ?: 120L
            val result = ideService.runCommand(command, timeout)
            val text = buildString {
                if (result.output.isNotBlank()) appendLine(result.output)
                if (result.error.isNotBlank()) appendLine("STDERR:\n${result.error}")
                appendLine("Exit code: ${result.exitCode}")
                if (result.timedOut) appendLine("(Command timed out)")
            }
            listOf(UIMessagePart.Text(text.trimEnd()))
        },
    )

    private val getTerminalOutput = Tool(
        name = "getTerminalOutput",
        description = "Gets recent terminal transcript output.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("lines") { put("type", "integer"); put("description", "Number of recent lines to retrieve") }
                },
                required = emptyList<String>(),
            )
        },
        execute = { args ->
            val lines = args.asJsonObject["lines"]?.asJsonPrimitive?.asInt
            val output = ideService.getTerminalOutput(lines)
            listOf(UIMessagePart.Text(output.ifEmpty { "No terminal output available" }))
        },
    )
}
