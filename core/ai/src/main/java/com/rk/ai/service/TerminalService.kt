package com.rk.ai.service

import com.rk.ai.IdeBridge
import com.rk.ai.session.AiSessionManager
import com.rk.exec.ShellUtils
import com.rk.terminal.SessionService
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TerminalService {

    suspend fun runCommand(command: String, timeoutSeconds: Long): CommandResult {
        val workspace = IdeBridge.primaryWorkspacePath()
        val workingDir = if (workspace.isNotBlank() && java.io.File(workspace).exists()) {
            workspace
        } else {
            // Fall back to the active terminal session's working directory or home
            val sessionWorkDir = AiSessionManager.cwd
            if (!sessionWorkDir.isNullOrBlank()) sessionWorkDir
            else com.rk.file.sandboxHomeDir().absolutePath
        }
        val result = ShellUtils.runUbuntu(
            workingDir,
            "/bin/bash", "-lc", command,
            timeoutSeconds = timeoutSeconds.coerceIn(1, 600),
        )
        return CommandResult(
            output = result.output, error = result.error,
            exitCode = result.exitCode, timedOut = result.timedOut
        )
    }

    suspend fun getTerminalOutput(lines: Int?): String {
        // Prioritize active user terminal session
        val userSession = SessionService.instance.get()?.getCurrentTerminalSession()
        val session: TerminalSession = userSession 
            ?: AiSessionManager.session 
            ?: return "No active terminal session found"

        if (!session.isRunning) return "Terminal session is not running"
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
