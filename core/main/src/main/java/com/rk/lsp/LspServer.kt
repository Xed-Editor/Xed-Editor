package com.rk.lsp

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import com.rk.activities.main.MainActivity
import com.rk.exec.TerminalCommand
import com.rk.exec.launchTerminal
import com.rk.file.FileObject
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.applyHighlightingAndConnectLSP
import com.rk.theme.greenStatus
import com.rk.theme.yellowStatus
import java.io.File
import java.net.URI
import org.eclipse.lsp4j.ServerCapabilities

abstract class ScriptedLspServer : LspServer() {
    abstract val installScript: File
    abstract val installId: String

    override fun install(context: Context) = launchInstaller(context)

    override fun uninstall(context: Context) = launchInstaller(context, "--uninstall")

    override fun update(context: Context) = launchInstaller(context, "--update")

    protected fun launchInstaller(context: Context, vararg flags: String) {
        launchTerminal(
            context = context,
            terminalCommand =
                TerminalCommand(
                    exe = "/bin/bash",
                    args = arrayOf(installScript.absolutePath, *flags),
                    id = installId,
                    env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
                ),
        )
    }
}

abstract class LspServer {
    suspend fun startAllInstances(): List<EditorTab> {
        val connectedEditors = mutableListOf<EditorTab>()
        _instances.forEach { connectedEditors.addAll(it.start()) }
        return connectedEditors
    }

    suspend fun stopAllInstances() {
        _instances.forEach { it.stop() }
    }

    suspend fun disconnectAllInstances() {
        _instances.forEach { it.disconnect() }
    }

    suspend fun restartAllInstances() {
        _instances.forEach { it.restart() }
    }

    fun connectAllSuitableEditors(excludedEditors: List<EditorTab> = emptyList()) {
        val suitableTabs =
            MainActivity.instance!!.viewModel.run {
                tabs.filterIsInstance<EditorTab>().filter {
                    !excludedEditors.contains(it) && this@LspServer.supportedExtensions.contains(it.file.getExtension())
                }
            }
        suitableTabs.forEach { it.applyHighlightingAndConnectLSP() }
    }

    private val _instances = mutableStateListOf<LspServerInstance>()
    val instances: List<LspServerInstance>
        get() = _instances.toList()

    fun addInstance(instance: LspServerInstance) {
        _instances.add(instance)
    }

    fun removeInstance(instance: LspServerInstance) {
        _instances.remove(instance)
    }

    abstract suspend fun isInstalled(context: Context): Boolean

    abstract fun install(context: Context)

    abstract fun uninstall(context: Context)

    abstract suspend fun isUpdatable(context: Context): Boolean

    abstract fun update(context: Context)

    abstract fun getConnectionConfig(): LspConnectionConfig

    open suspend fun beforeConnect() {}

    open suspend fun onInitialize(lspConnector: LspConnector) {}

    open fun getInitializationOptions(uri: URI?): Any? = null

    open fun isSupported(file: FileObject): Boolean {
        return supportedExtensions.contains(file.getExtension().lowercase())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LspServer
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    open val expectedCapabilities: ServerCapabilities? = null

    open val canBeUninstalled = true

    abstract val id: String
    abstract val languageName: String
    abstract val serverName: String
    abstract val supportedExtensions: List<String>
    abstract val icon: Int?
}

@Composable
fun LspServer.getDominantStatusColor(): Color? {
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
