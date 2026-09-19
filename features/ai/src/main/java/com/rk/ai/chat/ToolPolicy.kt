package com.rk.ai.chat

import com.rk.ai.settings.PermissionMode
import com.rk.ai.tools.AiTool
import com.rk.ai.tools.AiToolKind

/** What the permission gate decides for a single tool call. */
internal enum class ToolGate {
    RunNow,

    AskUser,

    Blocked,
}

/**
 * Decides whether a tool may run, must ask, or is blocked outright.
 *
 * The rules are deliberately a pure function of the tool's declared [AiToolKind] and
 * `isDestructive` flag, plus the user's mode and whether this file was already allowed in the chat.
 */
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

/**
 * Thrown when the user rejects a tool call.
 *
 * It aborts the whole run rather than being reported back to the model as a failed tool: the model
 * would otherwise simply ask for the same permission again, leaving the approval prompt on screen.
 */
internal class ToolDeniedException(toolName: String) : Exception("Tool '$toolName' was denied by the user.")
