package com.rk.lsp

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.events.Events
import com.rk.events.LSPEvent
import com.rk.file.FileObject
import com.rk.icons.Error
import com.rk.icons.XedIcons
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.applyHighlightingAndConnectLSP
import com.rk.theme.greenStatus
import com.rk.theme.yellowStatus
import io.github.rosemoe.sora.lsp.client.languageserver.wrapper.LanguageServerWrapper
import io.github.rosemoe.sora.lsp.editor.LspProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType

enum class LspConnectionStatus {
    NOT_RUNNING,
    RUNNING,
    STARTING,
    STOPPING,
    RESTARTING,
    CRASHED,
    TIMEOUT,
}

enum class MessageSource {
    RPC,
    LSP,
    Runtime,
    Client,
}

data class LspLogEntry(
    val source: MessageSource,
    val type: MessageType?,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
)

data class LspServerInstance(
    val server: LspServer,
    internal val lspProject: LspProject,
    val projectRoot: FileObject,
) {
    val id = "${server.id}_${projectRoot.getAbsolutePath().hashCode()}"

    var status by mutableStateOf(LspConnectionStatus.NOT_RUNNING)
    var startupTime by mutableLongStateOf(-1)
    private val logs = mutableStateListOf<LspLogEntry>()
    var hasError by mutableStateOf(false)

    fun addLog(messageParams: MessageParams) {
        addLog(
            LspLogEntry(
                type = messageParams.type,
                message = messageParams.message,
                source = MessageSource.LSP,
            )
        )
    }

    fun addLog(lspLogEntry: LspLogEntry) {
        if (lspLogEntry.type == MessageType.Error) {
            hasError = true
        }

        logs.add(lspLogEntry)

        if (logs.size > Settings.lsp_log_limit) {
            val removeCount = logs.size - Settings.lsp_log_limit
            logs.removeRange(0, removeCount)
        }

        DefaultScope.launch {
            Events.publish(LSPEvent.LogEntryWritten(this@LspServerInstance, lspLogEntry))
        }
    }

    fun getLspLogs() = logs.toList()

    fun getWrapper(): LanguageServerWrapper? {
        return server.supportedExtensions.firstOrNull()?.let {
            lspProject.getLanguageServerWrapper(it, server.serverName)
        }
            ?: run {
                hasError = true
                addLog(
                    LspLogEntry(
                        MessageSource.Client,
                        MessageType.Error,
                        "Language server instance not found...",
                    )
                )
                return null
            }
    }

    /** Stops this language server instance */
    suspend fun stop() {
        withContext(Dispatchers.IO) {
            addLog(
                LspLogEntry(
                    MessageSource.Client,
                    MessageType.Info,
                    "User stopped language server instance...",
                )
            )
            val wrapper = getWrapper() ?: return@withContext
            DefinitionPrevention.register(lspProject, server)
            try {
                wrapper.stop(true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Restarts this language server instance */
    suspend fun restart() {
        withContext(Dispatchers.IO) {
            addLog(
                LspLogEntry(
                    MessageSource.Client,
                    MessageType.Info,
                    "User restarted language server instance...",
                )
            )
            val wrapper = getWrapper() ?: return@withContext
            try {
                wrapper.restartAndReconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Starts this language server instance
     *
     * @return List of editors that were reconnected
     */
    suspend fun start(): List<EditorTab> {
        return withContext(Dispatchers.IO) {
            addLog(
                LspLogEntry(
                    MessageSource.Client,
                    MessageType.Info,
                    "User started language server instance...",
                )
            )
            val wrapper = getWrapper() ?: return@withContext emptyList()
            hasError = false
            DefinitionPrevention.unregister(lspProject, server)
            try {
                wrapper.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val reconnectedEditors = mutableListOf<EditorTab>()
            reconnect(reconnectedEditors)
            return@withContext reconnectedEditors
        }
    }

    private fun reconnect(reconnectedEditors: MutableList<EditorTab>) {
        val editors = lspProject.getEditors()
        val filteredEditors = editors.filter { server.supportedExtensions.contains(it.fileExt) }
        filteredEditors.forEach { editor ->
            val matchingEditorTab =
                MainActivity.instance!!.viewModel.run {
                    tabs.filterIsInstance<EditorTab>().find { it.editorState.editor.get() == editor.editor }
                } ?: return@forEach
            matchingEditorTab.applyHighlightingAndConnectLSP()
            reconnectedEditors.add(matchingEditorTab)
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            val wrapper = getWrapper() ?: return@withContext
            server.supportedExtensions.forEach {
                lspProject.removeServerDefinition(
                    it,
                    server.serverName,
                )
            }
            lspProject.getEditors().forEach { wrapper.disconnect(it) }
        }
    }
}

@Composable
fun LspServerInstance.getStatusColor(): Color? {
    return if (status == LspConnectionStatus.CRASHED || status == LspConnectionStatus.TIMEOUT || hasError) {
        MaterialTheme.colorScheme.error
    } else if (
        status == LspConnectionStatus.STARTING ||
            status == LspConnectionStatus.RESTARTING ||
            status == LspConnectionStatus.STOPPING
    ) {
        MaterialTheme.colorScheme.yellowStatus
    } else if (status == LspConnectionStatus.RUNNING) {
        MaterialTheme.colorScheme.greenStatus
    } else null
}

@Composable
fun LspServerInstance.getStatusText(): String {
    return when {
        status == LspConnectionStatus.CRASHED -> stringResource(strings.status_crashed)
        status == LspConnectionStatus.TIMEOUT -> stringResource(strings.status_timeout)
        hasError -> stringResource(strings.error)
        status == LspConnectionStatus.STARTING -> stringResource(strings.status_starting)
        status == LspConnectionStatus.RESTARTING -> stringResource(strings.status_restarting)
        status == LspConnectionStatus.RUNNING -> stringResource(strings.status_running)
        status == LspConnectionStatus.STOPPING -> stringResource(strings.status_stopping)
        DefinitionPrevention.isServerPrevented(
            lspProject,
            server,
        ) -> stringResource(strings.status_not_running_forced)

        else -> stringResource(strings.status_not_running)
    }
}

@Composable
fun LspServerInstance.StatusIcon() {
    when {
        status == LspConnectionStatus.CRASHED || status == LspConnectionStatus.TIMEOUT || hasError -> {
            Icon(
                imageVector = XedIcons.Error,
                contentDescription = stringResource(strings.error),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        }

        status == LspConnectionStatus.STARTING ||
            status == LspConnectionStatus.RESTARTING ||
            status == LspConnectionStatus.STOPPING -> {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        status == LspConnectionStatus.RUNNING -> {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(strings.status_running),
                tint = MaterialTheme.colorScheme.greenStatus,
                modifier = Modifier.size(32.dp),
            )
        }

        status == LspConnectionStatus.NOT_RUNNING -> {}
    }
}
