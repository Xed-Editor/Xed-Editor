package com.rk.lsp

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.rk.file.FileObject
import com.rk.theme.greenStatus
import com.rk.theme.yellowStatus
import java.net.URI
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType

enum class LspConnectionStatus {
    NOT_RUNNING,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class LspLogEntry(val level: MessageType, val message: String, val timestamp: Long = System.currentTimeMillis())

@Composable
fun BaseLspServer.getConnectionColor(): Color? {
    return when (status) {
        LspConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.yellowStatus
        LspConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.greenStatus
        LspConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> null
    }
}

abstract class BaseLspServer {
    var status by mutableStateOf(LspConnectionStatus.NOT_RUNNING)
    val logs = mutableStateListOf<LspLogEntry>()

    fun addLog(messageParams: MessageParams) {
        logs.add(LspLogEntry(level = messageParams.type, message = messageParams.message))
    }

    fun addLog(lspLogEntry: LspLogEntry) {
        logs.add(lspLogEntry)
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
