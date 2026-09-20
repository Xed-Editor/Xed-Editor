package com.rk.ai.tools

import com.rk.ai.model.AiTodo
import com.rk.ai.model.ToolCallStatus

/** The view a tool handler gets on its agent run; [AiTool.execute] is for argument-only tools. */
interface AiToolSession {
    /** 0 for the main agent, increasing for each nested sub-agent. */
    val depth: Int

    fun updateCall(status: ToolCallStatus, text: String)

    suspend fun askUser(question: String, options: List<String>): String

    fun setGoal(goal: String?)

    fun setTodos(todos: List<AiTodo>)

    /** Runs a sub-agent on a fresh history; fails when the maximum nesting depth is reached. */
    suspend fun runSubAgent(prompt: String): String
}
