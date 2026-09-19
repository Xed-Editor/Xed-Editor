package com.rk.ai.tools

import com.rk.ai.model.AiTodo
import com.rk.ai.model.ToolCallStatus

/**
 * The view a tool handler gets on the agent run it is executing inside.
 *
 * Tools that only transform their arguments should use [AiTool.execute] instead; this interface is
 * for the small number of tools that need to talk to the user or change session state, such as
 * `ask_user`, `set_goal` or `spawn_agent`.
 */
interface AiToolSession {
    /** 0 for the main agent, increasing for each nested sub-agent. */
    val depth: Int

    /** Updates the status/body of the tool card that is currently running. */
    fun updateCall(status: ToolCallStatus, text: String)

    /** Suspends until the user answers, returning their reply. */
    suspend fun askUser(question: String, options: List<String>): String

    /** Publishes (or clears, with null) the goal shown above the transcript. */
    fun setGoal(goal: String?)

    /** Publishes the task list shown in the tasks sheet. */
    fun setTodos(todos: List<AiTodo>)

    /**
     * Runs a sub-agent on a fresh history and returns its report. Fails when the maximum nesting
     * depth has been reached.
     */
    suspend fun runSubAgent(prompt: String): String
}
