package com.rk.lsp

import android.content.Context
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
import com.rk.activities.main.MainActivity
import com.rk.file.FileObject
import com.rk.icons.Error
import com.rk.icons.XedIcons
import com.rk.resources.strings
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.applyHighlightingAndConnectLSP
import com.rk.theme.greenStatus
import com.rk.theme.yellowStatus
import io.github.rosemoe.sora.lsp.client.languageserver.wrapper.LanguageServerWrapper
import io.github.rosemoe.sora.lsp.editor.LspProject
import java.net.URI
import kotlinx.coroutines.Dispatchers
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

data class LspLogEntry(val level: MessageType, val message: String, val timestamp: Long = System.currentTimeMillis())

@Composable
fun BaseLspServerInstance.getStatusColor(): Color? {
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
fun BaseLspServerInstance.getStatusText(): String {
    return if (status == LspConnectionStatus.CRASHED) stringResource(strings.status_crashed)
    else if (status == LspConnectionStatus.TIMEOUT) stringResource(strings.status_timeout)
    else if (hasError) stringResource(strings.error)
    else if (status == LspConnectionStatus.STARTING) stringResource(strings.status_starting)
    else if (status == LspConnectionStatus.RESTARTING) stringResource(strings.status_restarting)
    else if (status == LspConnectionStatus.RUNNING) stringResource(strings.status_running)
    else if (status == LspConnectionStatus.STOPPING) stringResource(strings.status_stopping)
    else if (DefinitionPrevention.isServerPrevented(lspProject, server))
        stringResource(strings.status_not_running_forced)
    else stringResource(strings.status_not_running)
}

@Composable
fun BaseLspServerInstance.StatusIcon() {
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

@Composable
fun BaseLspServer.getDominantStatusColor(): Color? {
    val hasAnyError = instances.any { it.hasError }
    if (hasAnyError) return MaterialTheme.colorScheme.error

    val dominantStatus = instances.maxByOrNull { it.status.ordinal }?.status ?: LspConnectionStatus.NOT_RUNNING
    return when (dominantStatus) {
        LspConnectionStatus.CRASHED,
        LspConnectionStatus.TIMEOUT -> MaterialTheme.colorScheme.error
        LspConnectionStatus.STARTING,
        LspConnectionStatus.RESTARTING,
        LspConnectionStatus.STOPPING -> MaterialTheme.colorScheme.yellowStatus
        LspConnectionStatus.RUNNING -> MaterialTheme.colorScheme.greenStatus
        else -> null
    }
}

data class BaseLspServerInstance(
    val server: BaseLspServer,
    internal val lspProject: LspProject,
    val projectRoot: FileObject,
) {
    val id = "${server.id}_${projectRoot.getAbsolutePath().hashCode()}"

    var status by mutableStateOf(LspConnectionStatus.NOT_RUNNING)
    var startupTime by mutableLongStateOf(-1)
    val logs = mutableStateListOf<LspLogEntry>()
    var hasError by mutableStateOf(false)

    fun addLog(messageParams: MessageParams) {
        logs.add(LspLogEntry(level = messageParams.type, message = messageParams.message))
    }

    fun addLog(lspLogEntry: LspLogEntry) {
        if (lspLogEntry.level == MessageType.Error) hasError = true
        logs.add(lspLogEntry)
    }

    fun getWrapper(): LanguageServerWrapper? {
        return server.supportedExtensions.firstOrNull()?.let {
            lspProject.getLanguageServerWrapper(it, server.serverName)
        }
            ?: run {
                hasError = true
                addLog(LspLogEntry(MessageType.Error, "Language server instance not found..."))
                return null
            }
    }

    /** Stops this language server instance */
    suspend fun stop() {
        withContext(Dispatchers.IO) {
            addLog(LspLogEntry(MessageType.Info, "User stopped language server instance..."))
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
            addLog(LspLogEntry(MessageType.Info, "User restarted language server instance..."))
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
            addLog(LspLogEntry(MessageType.Info, "User started language server instance..."))
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
            server.supportedExtensions.forEach { lspProject.removeServerDefinition(it, server.serverName) }
            lspProject.getEditors().forEach { wrapper.disconnect(it) }
        }
    }
}

abstract class BaseLspServer {
    //    suspend fun startAllInstances(): List<EditorTab> {
    //        val connectedEditors = mutableListOf<EditorTab>()
    //        instances.forEach { connectedEditors.addAll(it.start()) }
    //        return connectedEditors
    //    }
    //
    //    suspend fun stopAllInstances() {
    //        instances.forEach { it.stop() }
    //    }

    suspend fun disconnectAllInstances() {
        instances.forEach { it.disconnect() }
    }

    suspend fun restartAllInstances() {
        instances.forEach { it.restart() }
    }

    fun connectAllSuitableEditors(excludedEditors: List<EditorTab> = emptyList()) {
        val suitableTabs =
            MainActivity.instance!!.viewModel.run {
                tabs.filterIsInstance<EditorTab>().filter {
                    !excludedEditors.contains(it) &&
                        this@BaseLspServer.supportedExtensions.contains(it.file.getExtension())
                }
            }
        suitableTabs.forEach { it.applyHighlightingAndConnectLSP() }
    }

    var instances = mutableStateListOf<BaseLspServerInstance>()

    fun addInstance(instance: BaseLspServerInstance) {
        instances.add(instance)
    }

    fun removeInstance(instance: BaseLspServerInstance) {
        instances.remove(instance)
    }

    abstract fun isInstalled(context: Context): Boolean

    abstract fun install(context: Context)

    abstract fun getConnectionConfig(): LspConnectionConfig

    open suspend fun beforeConnect() {}

    open suspend fun onInitialize(lspConnector: BaseLspConnector) {}

    open fun getInitializationOptions(uri: URI?): Any? = null

    open fun isSupported(file: FileObject): Boolean {
        return supportedExtensions.contains(file.getExtension().lowercase())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BaseLspServer
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    abstract val id: String
    abstract val languageName: String
    abstract val serverName: String
    abstract val supportedExtensions: List<String>
    abstract val icon: Int?
}
