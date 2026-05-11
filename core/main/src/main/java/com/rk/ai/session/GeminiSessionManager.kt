package com.rk.ai.session

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.activities.main.MainViewModel
import com.rk.ai.GeminiBridge
import com.rk.ai.bridge.server.GeminiBridgeServer
import com.rk.ai.service.GeminiIdeService
import com.rk.ai.service.GeminiIdeServiceImpl
import com.rk.tabs.editor.createGeminiSheetSession
import com.rk.xededitor.BuildConfig
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiSessionManager {
    var session by mutableStateOf<TerminalSession?>(null)
    var cwd by mutableStateOf<String?>(null)
    var bridgeServer: GeminiBridgeServer? = null
    var ideService: GeminiIdeService? = null

    private fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d("GeminiSessionManager", msg)
    }

    fun canReuseFor(requestedCwd: String): Boolean {
        if (session == null || !session!!.isRunning) return false
        val existingCwd = cwd ?: return false
        if (requestedCwd == existingCwd) return true
        // Lenient reuse for common roots if needed
        if (existingCwd == "/" || existingCwd == "/storage/emulated/0" || existingCwd == "/home") return true
        return requestedCwd.startsWith("$existingCwd/")
    }

    suspend fun startSession(
        activity: Activity,
        viewModel: MainViewModel,
        workingDir: String,
        extraArgs: List<String> = emptyList()
    ): TerminalSession {
        d("startSession workingDir=$workingDir")
        stopSession()

        return withContext(Dispatchers.IO) {
            GeminiBridge.ensureStarted(viewModel)
            GeminiBridge.setWorkspacePath(workingDir)
            val bridgeInfo = GeminiBridge.getBridgeInfo()!!
            
            withContext(Dispatchers.Main) {
                // The actual server is managed by GeminiBridge object for discovery file consistency
                // but we can track it here if we want direct access to the service
                ideService = GeminiIdeServiceImpl(viewModel) 
                
                // Create CLI session
                val newSession = createGeminiSheetSession(
                    activity = activity,
                    bridge = bridgeInfo,
                    workingDir = workingDir,
                    extraArgs = extraArgs,
                )
                
                session = newSession
                cwd = workingDir
                newSession
            }
        }
    }

    fun stopSession() {
        d("stopSession")
        session?.finishIfRunning()
        session = null
        cwd = null
        GeminiBridge.stop()
        ideService = null
    }
}
