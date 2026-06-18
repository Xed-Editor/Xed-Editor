package com.rk.terminal

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.rk.terminal.virtualkeys.VirtualKeysView
import java.lang.ref.WeakReference

interface TerminalController {
    val sessions: List<TerminalSession>
    val sessionIds: List<String>
    val currentSessionId: String?
    val currentSession: TerminalSession?
    
    fun changeSession(sessionId: String)
    fun createSession(sessionId: String): TerminalSession
    fun terminateSession(sessionId: String)

    var terminalViewRef: WeakReference<TerminalView?>
    var virtualKeysViewRef: WeakReference<VirtualKeysView?>
}

class InMemoryTerminalController(private val context: Context) : TerminalController {
    private val sessionsMap = mutableMapOf<String, TerminalSession>()
    private val sessionListState = mutableStateListOf<String>()
    private val currentSessionIdState = mutableStateOf<String?>(null)

    override val sessions: List<TerminalSession>
        get() = sessionListState.mapNotNull { sessionsMap[it] }

    override val sessionIds: List<String>
        get() = sessionListState

    override val currentSessionId: String?
        get() = currentSessionIdState.value

    override val currentSession: TerminalSession?
        get() = currentSessionId?.let { sessionsMap[it] }

    override var terminalViewRef = WeakReference<TerminalView?>(null)
    override var virtualKeysViewRef = WeakReference<VirtualKeysView?>(null)

    override fun createSession(sessionId: String): TerminalSession {
        val existing = sessionsMap[sessionId]
        if (existing != null) {
            return existing
        }
        val client = TerminalBackEnd(this)
        val session = MkSession.createSession(context, client, sessionId).first
        sessionsMap[sessionId] = session
        sessionListState.add(sessionId)
        if (currentSessionIdState.value == null) {
            currentSessionIdState.value = sessionId
        }
        return session
    }

    override fun terminateSession(sessionId: String) {
        sessionsMap[sessionId]?.apply {
            finishIfRunning()
        }
        sessionsMap.remove(sessionId)
        sessionListState.remove(sessionId)
        if (currentSessionIdState.value == sessionId) {
            currentSessionIdState.value = sessionListState.firstOrNull()
        }
    }

    override fun changeSession(sessionId: String) {
        val terminalView = terminalViewRef.get() ?: return
        val client = TerminalBackEnd(this)
        val session = sessionsMap[sessionId] ?: createSession(sessionId)

        session.updateTerminalSessionClient(client)
        terminalView.attachSession(session)
        terminalView.setTerminalViewClient(client)

        terminalView.apply {
            post {
                keepScreenOn = true
                isFocusableInTouchMode = true
                requestFocus()
            }
        }
        virtualKeysViewRef.get()?.apply { virtualKeysViewClient = com.rk.terminal.virtualkeys.VirtualKeysListener(terminalView.mTermSession) }

        currentSessionIdState.value = sessionId
    }
}
