package com.rk.ai.tools

import ai.koog.agents.core.tools.ToolParameterType
import com.rk.ai.settings.AiMemory
import com.rk.ai.settings.AiScratchpad

object StateTools {
    fun all(): List<AiTool> = listOf(saveMemory(), forgetMemory(), scratchpad())

    private fun saveMemory(): AiTool =
        AiTool(
            name = "save_memory",
            description =
                "Save a durable note about the user, this project or a preference. It is remembered " +
                    "across chats and listed in your system prompt from then on. Keep entries short " +
                    "and factual, and do not save things that are already obvious from the code.",
            kind = AiToolKind.Write,
            isDestructive = false,
            parameters =
                listOf(
                    AiToolParameter(
                        "content",
                        "The note to remember, e.g. 'The user prefers tabs and ktfmt formatting'.",
                        ToolParameterType.String,
                    )
                ),
            presenter =
                AiToolPresenter { args ->
                    ToolCallView("Save memory", null, listOfNotNull(toolDetail("Note", args.displayArg("content"))))
                },
        ) { args ->
            AiMemory.add(args.requireArg("content"))
            "Saved. Long-term memory now holds ${AiMemory.entries.size} note(s)."
        }

    private fun forgetMemory(): AiTool =
        AiTool(
            name = "forget_memory",
            description =
                "Delete one long-term memory entry by the number shown in the memory list in your " +
                    "system prompt. Use it when a note is wrong or no longer relevant.",
            kind = AiToolKind.Write,
            isDestructive = false,
            parameters =
                listOf(
                    AiToolParameter(
                        "index",
                        "1-based number of the memory entry to delete.",
                        ToolParameterType.Integer,
                    )
                ),
            presenter =
                AiToolPresenter { args ->
                    ToolCallView(
                        "Forget memory",
                        args.displayArg("index"),
                        listOfNotNull(toolField("Index", args.displayArg("index"))),
                    )
                },
        ) { args ->
            val index = args.argInt("index")
                ?: throw IllegalArgumentException("Missing or non-numeric argument 'index'")
            if (AiMemory.removeAt(index)) {
                "Removed memory $index. ${AiMemory.entries.size} note(s) left."
            } else {
                "There is no memory numbered $index."
            }
        }

    private fun scratchpad(): AiTool =
        AiTool(
            name = "scratchpad",
            description =
                "A private scratch pad for the current chat only. Use it to park notes, a plan or " +
                    "intermediate results you do not want to keep in the conversation, then read them " +
                    "back when needed. 'write' replaces the whole pad, 'append' adds a line, 'read' " +
                    "returns it. The pad is discarded when the chat is closed.",
            kind = AiToolKind.Write,
            isDestructive = false,
            parameters =
                listOf(
                    AiToolParameter(
                        "action",
                        "What to do with the pad.",
                        ToolParameterType.Enum(arrayOf("read", "write", "append")),
                    ),
                    AiToolParameter(
                        "content",
                        "Text for 'write' and 'append'. Ignored by 'read'.",
                        ToolParameterType.String,
                        required = false,
                    ),
                ),
            presenter =
                AiToolPresenter { args ->
                    ToolCallView(
                        "Scratch pad",
                        args.displayArg("action"),
                        listOfNotNull(toolDetail("Content", args.displayArg("content"))),
                    )
                },
        ) { args ->
            when (args.requireArg("action").trim().lowercase()) {
                "read" -> AiScratchpad.text.ifBlank { "(the scratch pad is empty)" }
                "write" -> {
                    AiScratchpad.write(args.requireArg("content"))
                    "Scratch pad replaced (${AiScratchpad.text.length} characters)."
                }
                "append" -> {
                    AiScratchpad.append(args.requireArg("content"))
                    "Appended to the scratch pad (${AiScratchpad.text.length} characters)."
                }
                else -> throw IllegalArgumentException("Unknown action; use read, write or append.")
            }
        }
}
