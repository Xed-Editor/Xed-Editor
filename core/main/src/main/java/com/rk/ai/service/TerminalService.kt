package com.rk.ai.service

import com.rk.ai.IdeBridge
import com.rk.ai.session.AiSessionManager
import com.rk.exec.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TerminalService {

    suspend fun runCommand(command: String, timeoutSeconds: Long): CommandResult {
        val result = ShellUtils.runUbuntu(
            IdeBridge.primaryWorkspacePath(),
            "/bin/bash", "-lc", command,
            timeoutSeconds = timeoutSeconds.coerceIn(1, 600),
        )
        return CommandResult(
            output = result.output, error = result.error,
            exitCode = result.exitCode, timedOut = result.timedOut
        )
    }

    suspend fun getTerminalOutput(lines: Int?): String {
        val session = AiSessionManager.session
        val agentName = AiSessionManager.currentAgent.displayName
        if (session == null || !session.isRunning) return "No active $agentName terminal session"
        val emulator = session.emulator ?: return "Terminal emulator not available"
        return withContext(Dispatchers.IO) {
            val full = synchronized(emulator) {
                emulator.screen.getTranscriptTextWithoutJoinedLines()
            }
            if (lines != null && lines > 0) {
                full.split("\n").takeLast(lines.coerceAtLeast(1)).joinToString("\n")
            } else full
        }
    }
}
