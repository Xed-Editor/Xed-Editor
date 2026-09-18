package com.rk.ai.chat

import com.rk.ai.settings.PermissionMode
import com.rk.ai.tools.AiTool
import com.rk.ai.tools.AiToolKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolGateTest {
    private fun tool(kind: AiToolKind, destructive: Boolean? = null) =
        if (destructive == null) {
            AiTool(name = "t", description = "d", kind = kind) { "" }
        } else {
            AiTool(name = "t", description = "d", kind = kind, isDestructive = destructive) { "" }
        }

    private val read = tool(AiToolKind.Read)
    private val write = tool(AiToolKind.Write)
    private val shell = tool(AiToolKind.Shell)
    private val network = tool(AiToolKind.Network)

    @Test
    fun readsNeverPromptInAnyMode() {
        PermissionMode.entries.forEach { mode ->
            assertEquals(
                "read tool under $mode",
                ToolGate.RunNow,
                gateFor(read, mode),
            )
        }
    }

    @Test
    fun explicitNonDestructiveToolNeverPrompts() {
        val inspecting = tool(AiToolKind.Shell, destructive = false)

        assertEquals(ToolGate.RunNow, gateFor(inspecting, PermissionMode.ASK))
        assertEquals(ToolGate.RunNow, gateFor(inspecting, PermissionMode.PLAN))
    }

    @Test
    fun askModePromptsForWritesAndShell() {
        assertEquals(ToolGate.AskUser, gateFor(write, PermissionMode.ASK))
        assertEquals(ToolGate.AskUser, gateFor(shell, PermissionMode.ASK))
    }

    @Test
    fun mutagenicNetworkRequestsAreGatedLikeShell() {
        assertEquals(ToolGate.AskUser, gateFor(network, PermissionMode.ASK))
        assertEquals(ToolGate.AskUser, gateFor(network, PermissionMode.ACCEPT_EDITS))
        assertEquals(ToolGate.Blocked, gateFor(network, PermissionMode.PLAN))
        assertEquals(ToolGate.RunNow, gateFor(network, PermissionMode.YOLO))
    }

    @Test
    fun httpGetNeverPromptsBecauseItIsNonDestructive() {
        val httpGet = tool(AiToolKind.Network, destructive = false)

        PermissionMode.entries.forEach { mode ->
            assertEquals("http_get under $mode", ToolGate.RunNow, gateFor(httpGet, mode))
        }
    }

    @Test
    fun acceptEditsRunsWritesButAsksForShell() {
        assertEquals(ToolGate.RunNow, gateFor(write, PermissionMode.ACCEPT_EDITS))
        assertEquals(ToolGate.AskUser, gateFor(shell, PermissionMode.ACCEPT_EDITS))
    }

    @Test
    fun planModeBlocksDestructiveTools() {
        assertEquals(ToolGate.Blocked, gateFor(write, PermissionMode.PLAN))
        assertEquals(ToolGate.Blocked, gateFor(shell, PermissionMode.PLAN))
    }

    @Test
    fun yoloRunsEverything() {
        listOf(read, write, shell, network).forEach { candidate ->
            assertEquals(ToolGate.RunNow, gateFor(candidate, PermissionMode.YOLO))
        }
    }

    @Test
    fun rememberedFileRunsWithoutPromptingAgain() {
        assertEquals(ToolGate.RunNow, gateFor(write, PermissionMode.ASK, alreadyApproved = true))
    }

    @Test
    fun rememberedFileStillBlockedInPlanMode() {
        assertEquals(ToolGate.Blocked, gateFor(write, PermissionMode.PLAN, alreadyApproved = true))
    }

    @Test
    fun rememberedFileDoesNotAffectNonDestructiveReads() {
        assertEquals(ToolGate.RunNow, gateFor(read, PermissionMode.ASK, alreadyApproved = true))
    }

    @Test
    fun approvalIsIgnoredWhenNotRemembered() {
        assertEquals(ToolGate.AskUser, gateFor(write, PermissionMode.ASK, alreadyApproved = false))
    }
}
