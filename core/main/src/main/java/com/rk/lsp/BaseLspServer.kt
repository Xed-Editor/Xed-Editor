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
import com.rk.file.FileObject
import com.rk.icons.Error
import com.rk.icons.XedIcons
import com.rk.resources.strings
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
    STARTING,
    CONNECTED,
    STOPPING,
    CRASHED,
    TIMEOUT,
    RESTARTING,
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
    } else if (status == LspConnectionStatus.CONNECTED) {
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
    else if (status == LspConnectionStatus.CONNECTED) stringResource(strings.status_connected)
    else if (status == LspConnectionStatus.STOPPING) stringResource(strings.status_stopping)
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
        status == LspConnectionStatus.CONNECTED -> {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(strings.status_connected),
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
        LspConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.greenStatus
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

    fun removeDefinition() {
        return server.supportedExtensions.firstOrNull()?.let {
            lspProject.removeServerDefinition(it, server.serverName)
        }
            ?: run {
                hasError = true
                addLog(LspLogEntry(MessageType.Error, "Language server definition not found..."))
            }
    }

    /** Stops this language server instance */
    suspend fun stop() {
        withContext(Dispatchers.IO) {
            addLog(LspLogEntry(MessageType.Info, "User stopped language server instance..."))
            val wrapper = getWrapper() ?: return@withContext
            hasError = false
            removeDefinition()
            wrapper.stop(true)
        }
    }

    /** Restarts this language server instance */
    suspend fun restart() {
        withContext(Dispatchers.IO) {
            addLog(LspLogEntry(MessageType.Info, "User restarted language server instance..."))
            val wrapper = getWrapper() ?: return@withContext
            hasError = false
            wrapper.restartAndReconnect()
        }
    }
}

abstract class BaseLspServer {
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

    open suspend fun connectionSuccess(lspConnector: BaseLspConnector) {}

    open suspend fun connectionFailure(msg: String?) {}

    open fun getInitializationOptions(uri: URI?): Any? = null

    abstract fun isSupported(file: FileObject): Boolean

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
