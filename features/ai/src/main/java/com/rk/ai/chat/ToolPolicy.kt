package com.rk.ai.chat

import com.rk.ai.settings.PermissionMode
import com.rk.ai.tools.AiTool
import com.rk.ai.tools.AiToolKind

internal enum class ToolGate {
    RunNow,

    AskUser,

    Blocked,
}

/** Decides whether a tool runs, asks or is blocked, from its [AiToolKind], flags and the user's mode. */
internal fun gateFor(
    tool: AiTool,
    mode: PermissionMode,
    alreadyApproved: Boolean = false,
): ToolGate =
    when {
        !tool.isDestructive -> ToolGate.RunNow
        mode == PermissionMode.PLAN -> ToolGate.Blocked
        alreadyApproved -> ToolGate.RunNow
        mode == PermissionMode.ASK -> ToolGate.AskUser
        mode == PermissionMode.ACCEPT_EDITS ->
            if (tool.kind == AiToolKind.Write) ToolGate.RunNow else ToolGate.AskUser
        else -> ToolGate.RunNow
    }

/** Thrown when the user rejects a tool call; aborts the run so the model cannot re-ask. */
internal class ToolDeniedException(toolName: String) : Exception("Tool '$toolName' was denied by the user.")
