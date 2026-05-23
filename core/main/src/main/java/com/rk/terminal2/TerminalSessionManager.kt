package com.rk.terminal2

import android.util.Log
import com.rk.terminal.SessionId
import com.rk.terminal.SessionInfo
import com.rk.terminal.SessionPwd
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TerminalSessionData(
    val id: SessionId,
    val session: TerminalSession,
    val workingDirectory: String,
    val createdAt: Long = System.currentTimeMillis(),
    val title: String = id,
    val isRunning: Boolean = false,
    val exitCode: Int? = null,
)

data class TerminalManagerState(
    val sessions: List<TerminalSessionData> = emptyList(),
    val currentSessionId: SessionId = "main",
    val sessionCount: Int = 0,
)

class TerminalSessionManager {
    private val TAG = "TerminalSessionManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sessions = ConcurrentHashMap<SessionId, TerminalSessionData>()
    private val _state = MutableStateFlow(TerminalManagerState())
    val state: StateFlow<TerminalManagerState> = _state.asStateFlow()

    private var sessionIdCounter = 0

    fun createSession(
        id: SessionId? = null,
        workingDir: String,
        shellPath: String = "/system/bin/sh",
        args: Array<String> = emptyArray(),
        env: Array<String> = emptyArray(),
        client: TerminalSessionClient? = null,
        scrollbackRows: Int = 1000,
    ): TerminalSessionData {
        val sessionId = id ?: generateSessionId()
        val session = TerminalSession(
            shellPath,
            workingDir,
            args,
            env,
            scrollbackRows,
            client,
        ).also {
            it.mSessionName = sessionId
            it.finishIfRunning()
        }

        val data = TerminalSessionData(
            id = sessionId,
            session = session,
            workingDirectory = workingDir,
            title = sessionId,
        )

        sessions[sessionId] = data
        updateState()
        return data
    }

    fun getSession(id: SessionId): TerminalSession? = sessions[id]?.session

    fun getSessionData(id: SessionId): TerminalSessionData? = sessions[id]

    fun currentSession(): TerminalSession? = sessions[_state.value.currentSessionId]?.session

    fun switchSession(id: SessionId) {
        if (sessions.containsKey(id)) {
            _state.value = _state.value.copy(currentSessionId = id)
        }
    }

    fun killSession(id: SessionId) {
        sessions[id]?.let { data ->
            try {
                data.session.finishIfRunning()
            } catch (e: Exception) {
                Log.w(TAG, "Error killing session $id", e)
            }
            sessions.remove(id)
            updateState()
        }
    }

    fun killAll() {
        sessions.keys.toList().forEach { killSession(it) }
    }

    fun renameSession(id: SessionId, newName: String) {
        sessions[id]?.let { data ->
            sessions[id] = data.copy(title = newName)
            data.session.mSessionName = newName
            updateState()
        }
    }

    fun updateWorkingDirectory(id: SessionId, newDir: String) {
        sessions[id]?.let { data ->
            sessions[id] = data.copy(workingDirectory = newDir)
        }
    }

    fun writeToSession(id: SessionId, text: String) {
        sessions[id]?.session?.write(text)
    }

    fun writeToCurrentSession(text: String) {
        currentSession()?.write(text)
    }

    fun getSessionOutput(id: SessionId, lines: Int? = null): String {
        val session = sessions[id]?.session ?: return ""
        val text = session.getEmulator()?.getScreen()?.getTranscriptText()?.toString() ?: ""
        return if (lines != null && lines > 0) {
            val allLines = text.split("\n")
            allLines.takeLast(lines).joinToString("\n")
        } else text
    }

    val currentSessionId: SessionId get() = _state.value.currentSessionId

    val sessionCount: Int get() = sessions.size

    val isCurrentSessionRunning: Boolean
        get() = sessions[_state.value.currentSessionId]?.session?.isRunning == true

    private fun generateSessionId(): SessionId {
        sessionIdCounter++
        return "session-$sessionIdCounter"
    }

    private fun updateState() {
        _state.value = TerminalManagerState(
            sessions = sessions.values.toList(),
            currentSessionId = _state.value.currentSessionId,
            sessionCount = sessions.size,
        )
    }

    fun startHealthMonitor(intervalMs: Long = 5000L) {
        scope.launch {
            while (isActive) {
                val dead = mutableListOf<SessionId>()
                sessions.forEach { (id, data) ->
                    if (!data.session.isRunning) {
                        dead.add(id)
                    }
                }
                dead.forEach { killSession(it) }
                delay(intervalMs)
            }
        }
    }
}
