package com.rk.ai.bridge.server

import android.util.Log
import com.rk.ai.service.IdeService
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class McpNotificationManager(
    private val ideServiceProvider: () -> IdeService,
) {
    companion object {
        private const val TAG = "McpNotificationManager"
        private const val FILE_WATCH_INTERVAL_MS = 5000L
    }

    private var watchJob: Job? = null
    private val fileTimestamps = ConcurrentHashMap<String, Long>()
    private var server: Server? = null

    fun startWatching(server: Server, workspacePaths: List<String>) {
        this.server = server
        scanFiles(workspacePaths)

        watchJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(FILE_WATCH_INTERVAL_MS)
                checkForChanges(workspacePaths)
            }
        }

        if (com.rk.xededitor.BuildConfig.DEBUG) {
            Log.d(TAG, "Started file watching for ${workspacePaths.size} workspace(s)")
        }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        fileTimestamps.clear()
        server = null
        if (com.rk.xededitor.BuildConfig.DEBUG) {
            Log.d(TAG, "Stopped file watching")
        }
    }

    private fun scanFiles(workspacePaths: List<String>) {
        for (ws in workspacePaths) {
            val root = File(ws)
            if (!root.exists()) continue
            try {
                root.walkTopDown()
                    .maxDepth(5)
                    .onEnter { !it.name.startsWith(".") && it.name != "node_modules" && it.name != "build" }
                    .filter { it.isFile }
                    .take(1000)
                    .forEach { file ->
                        fileTimestamps[file.absolutePath] = file.lastModified()
                    }
            } catch (_: Exception) {
                // skip
            }
        }
    }

    private suspend fun checkForChanges(workspacePaths: List<String>) {
        val srv = server ?: return
        val changed = mutableListOf<String>()

        for (ws in workspacePaths) {
            val root = File(ws)
            if (!root.exists()) continue
            try {
                root.walkTopDown()
                    .maxDepth(5)
                    .onEnter { !it.name.startsWith(".") && it.name != "node_modules" && it.name != "build" }
                    .filter { it.isFile }
                    .take(1000)
                    .forEach { file ->
                        val lastModified = file.lastModified()
                        val previous = fileTimestamps[file.absolutePath]
                        if (previous == null) {
                            fileTimestamps[file.absolutePath] = lastModified
                        } else if (lastModified > previous) {
                            fileTimestamps[file.absolutePath] = lastModified
                            changed.add(file.absolutePath)
                        }
                    }
            } catch (_: Exception) {
                // skip
            }
        }

        if (changed.isNotEmpty()) {
            try {
                for (sessionKey in srv.sessions.keys) {
                    srv.sendResourceUpdated(
                        sessionId = sessionKey.toString(),
                        notification = ResourceUpdatedNotification(
                            params = io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotificationParams(
                                uri = "xed://workspace/tree",
                            ),
                        ),
                    )
                }
                if (com.rk.xededitor.BuildConfig.DEBUG) {
                    Log.d(TAG, "Sent resource update notification for ${changed.size} changed file(s)")
                }
            } catch (e: Exception) {
                if (com.rk.xededitor.BuildConfig.DEBUG) {
                    Log.w(TAG, "Failed to send notification: ${e.message}")
                }
            }
        }
    }

    suspend fun sendToolListChanged() {
        val srv = server ?: return
        try {
            for (sessionKey in srv.sessions.keys) {
                srv.sendToolListChanged(sessionKey.toString())
            }
        } catch (e: Exception) {
            if (com.rk.xededitor.BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to send tool list changed: ${e.message}")
            }
        }
    }

    suspend fun sendResourceListChanged() {
        val srv = server ?: return
        try {
            for (sessionKey in srv.sessions.keys) {
                srv.sendResourceListChanged(sessionKey.toString())
            }
        } catch (e: Exception) {
            if (com.rk.xededitor.BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to send resource list changed: ${e.message}")
            }
        }
    }

    suspend fun sendPromptListChanged() {
        val srv = server ?: return
        try {
            for (sessionKey in srv.sessions.keys) {
                srv.sendPromptListChanged(sessionKey.toString())
            }
        } catch (e: Exception) {
            if (com.rk.xededitor.BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to send prompt list changed: ${e.message}")
            }
        }
    }
}
