package com.rk.ai.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.termux.terminal.TerminalSession

class TerminalManagementService {

    private data class SessionEntry(val session: TerminalSession, val cwd: String)
    private val externalSessions = mutableMapOf<String, SessionEntry>()

    suspend fun listSessions(): JsonArray = withContext(Dispatchers.Main) {
        val result = JsonArray()
        val aiSession = com.rk.ai.session.AiSessionManager.session
        val aiCwd = com.rk.ai.session.AiSessionManager.cwd
        if (aiSession != null) {
            result.add(JsonObject().apply {
                addProperty("id", "ai-agent")
                addProperty("name", aiSession.mSessionName ?: "ai-agent")
                addProperty("type", "ai-agent")
                addProperty("running", aiSession.isRunning)
                aiCwd?.let { addProperty("cwd", it) }
            })
        }
        synchronized(externalSessions) {
            externalSessions.forEach { (id, entry) ->
                result.add(JsonObject().apply {
                    addProperty("id", id)
                    addProperty("name", entry.session.mSessionName ?: id)
                    addProperty("type", "external")
                    addProperty("running", entry.session.isRunning)
                    addProperty("cwd", entry.cwd)
                })
            }
        }
        result
    }

    suspend fun createSession(name: String, workingDir: String): String {
        val shell = "/system/bin/sh"
        val env = arrayOf("TERM=xterm-256color", "HOME=$workingDir")
        val session = withContext(Dispatchers.Main) {
            TerminalSession(shell, workingDir, emptyArray(), env,
                com.rk.settings.Settings.terminal_scrollback_buffer,
                com.rk.terminal.TerminalBackEnd()
            ).also { it.mSessionName = name }
        }
        val id = "ext-${System.currentTimeMillis()}"
        synchronized(externalSessions) { externalSessions[id] = SessionEntry(session, workingDir) }
        return "created terminal session '$name' with id=$id in $workingDir"
    }

    suspend fun killSession(sessionId: String): String = withContext(Dispatchers.IO) {
        if (sessionId == "ai-agent") {
            com.rk.ai.session.AiSessionManager.stopSession()
            return@withContext "stopped AI agent session"
        }
        synchronized(externalSessions) {
            externalSessions.remove(sessionId)?.let { entry ->
                entry.session.finishIfRunning()
                return@withContext "killed session $sessionId"
            }
        }
        "session $sessionId not found"
    }

    suspend fun writeToSession(sessionId: String, text: String): String = withContext(Dispatchers.IO) {
        val session = resolveSession(sessionId) ?: return@withContext "session $sessionId not found"
        if (!session.isRunning) return@withContext "session $sessionId is not running"
        session.write("$text\r")
        "written ${text.length} chars to session $sessionId"
    }

    suspend fun getSessionOutput(sessionId: String, lines: Int?): String = withContext(Dispatchers.IO) {
        val session = resolveSession(sessionId) ?: return@withContext "session $sessionId not found"
        val emulator = session.emulator ?: return@withContext "emulator not available"
        val full = synchronized(emulator) {
            emulator.screen.getTranscriptTextWithoutJoinedLines()
        }
        if (lines != null && lines > 0) {
            full.split("\n").takeLast(lines.coerceAtLeast(1)).joinToString("\n")
        } else full
    }

    private fun resolveSession(sessionId: String): TerminalSession? {
        if (sessionId == "ai-agent") return com.rk.ai.session.AiSessionManager.session
        synchronized(externalSessions) { return externalSessions[sessionId]?.session }
    }
}
