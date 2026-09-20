package com.rk.ai.tools

import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import com.rk.ai.model.AiTodo
import com.rk.ai.model.TodoStatus
import com.rk.ai.model.ToolCallStatus
import com.rk.ai.model.parseOptions
import com.rk.ai.model.parseTodos

internal const val SUB_AGENT_TOOL = "spawn_agent"

internal const val ASK_USER_TOOL = "ask_user"

internal const val SET_GOAL_TOOL = "set_goal"

internal const val WRITE_TODOS_TOOL = "write_todos"

/** Tools that talk to the running session rather than just transform their arguments. */
internal object AgentTools {
    fun all(): List<AiTool> = listOf(spawnAgent(), askUser(), setGoal(), writeTodos())

    private fun spawnAgent(): AiTool =
        AiTool(
            name = SUB_AGENT_TOOL,
            description =
                "Delegate a focused task to a sub-agent that works in its own fresh context and " +
                    "returns a concise report. Use it to keep your own context small: research, wide " +
                    "searches, or multi-step work whose intermediate output you do not need to keep. " +
                    "The sub-agent has the same tools and workspace but cannot ask the user questions, " +
                    "so put complete instructions and everything it needs into the prompt.",
            kind = AiToolKind.Read,
            isDestructive = false,
            parameters =
                listOf(
                    AiToolParameter(
                        "prompt",
                        "Complete, self-contained instructions for the sub-agent.",
                        ToolParameterType.String,
                    ),
                    AiToolParameter(
                        "description",
                        "Short label for the transcript, e.g. 'Find the auth code'.",
                        ToolParameterType.String,
                        required = false,
                    ),
                ),
            presenter =
                AiToolPresenter { args ->
                    ToolCallView(
                        "Sub-agent",
                        args.displayArg("description"),
                        listOfNotNull(toolDetail("Task", args.displayArg("prompt"))),
                    )
                },
            handler = { args, session ->
                val prompt = args.arg("prompt")?.takeIf { it.isNotBlank() }
                if (prompt == null) throw IllegalArgumentException("Missing required argument 'prompt'.")
                session.runSubAgent(prompt)
            },
        )

    private fun askUser(): AiTool =
        AiTool(
            name = ASK_USER_TOOL,
            description =
                "Ask the user to choose. Use it when a decision is genuinely theirs and you cannot " +
                    "infer the answer from the code: offer the realistic options, and the user can also " +
                    "type a custom reply. Pass an empty options list when you only want free text. The " +
                    "answer comes back as the tool result.",
            kind = AiToolKind.Read,
            isDestructive = false,
            mainAgentOnly = true,
            parameters =
                listOf(
                    AiToolParameter("question", "The question to put to the user.", ToolParameterType.String),
                    AiToolParameter(
                        "options",
                        "Choices to offer, best first. May be empty for a free-text question.",
                        ToolParameterType.List(ToolParameterType.String),
                        required = false,
                    ),
                ),
            presenter = AiToolPresenter { args -> askUserView(args) },
            handler = { args, session ->
                val question = args.arg("question")?.trim()?.takeIf { it.isNotEmpty() }
                if (question == null) throw IllegalArgumentException("Missing required argument 'question'.")
                session.updateCall(ToolCallStatus.AwaitingInput, "Waiting for your answer…")
                val answer = session.askUser(question, parseOptions(args["options"]))
                answer.ifBlank { "(the user sent an empty reply)" }
            },
        )

    private fun setGoal(): AiTool =
        AiTool(
            name = SET_GOAL_TOOL,
            description =
                "Declare the goal you are working towards, in one sentence. It stays visible to the " +
                    "user and is repeated back to you on later turns. Call it again to change the goal, " +
                    "or pass an empty string once the goal is met to clear it.",
            kind = AiToolKind.Read,
            isDestructive = false,
            mainAgentOnly = true,
            parameters =
                listOf(
                    AiToolParameter(
                        "goal",
                        "The goal, or an empty string to clear it.",
                        ToolParameterType.String,
                    )
                ),
            presenter =
                AiToolPresenter { args ->
                    val goal = args.displayArg("goal")
                    ToolCallView(if (goal == null) "Clear goal" else "Set goal", goal, emptyList())
                },
            handler = { args, session ->
                val requested = args.arg("goal")?.trim().orEmpty()
                session.setGoal(requested.ifEmpty { null })
                if (requested.isEmpty()) "Goal cleared." else "Goal set."
            },
        )

    private fun writeTodos(): AiTool =
        AiTool(
            name = WRITE_TODOS_TOOL,
            description =
                "Publish your task list. Always send the complete list, not just the changes: each call " +
                    "replaces the previous one. Keep exactly one task in_progress while you work and " +
                    "mark tasks completed as soon as they are done. Send an empty list when the work is " +
                    "finished and the list is no longer useful.",
            kind = AiToolKind.Read,
            isDestructive = false,
            mainAgentOnly = true,
            parameters =
                listOf(
                    AiToolParameter(
                        "todos",
                        "The full task list, in order.",
                        ToolParameterType.List(
                            ToolParameterType.Object(
                                properties =
                                    listOf(
                                        ToolParameterDescriptor(
                                            "content",
                                            "Short description of the task.",
                                            ToolParameterType.String,
                                        ),
                                        ToolParameterDescriptor(
                                            "status",
                                            "Task state.",
                                            ToolParameterType.Enum(
                                                arrayOf("pending", "in_progress", "completed")
                                            ),
                                        ),
                                    ),
                                requiredProperties = listOf("content", "status"),
                            )
                        ),
                    )
                ),
            presenter = AiToolPresenter { args -> writeTodosView(args) },
            handler = { args, session ->
                val todos: List<AiTodo> = parseTodos(args["todos"])
                session.setTodos(todos)
                todosSummary(todos)
            },
        )

    private fun todosSummary(todos: List<AiTodo>): String =
        if (todos.isEmpty()) {
            "Task list cleared."
        } else {
            "Task list: " +
                todos.joinToString("; ") { todo ->
                    "[${if (todo.status == TodoStatus.Completed) "x" else " "}] ${todo.content}"
                }
        }
}
