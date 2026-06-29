package com.rk.lsp

import android.app.Activity
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import com.rk.TerminalLauncher
import com.rk.activities.main.MainActivity
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.applyHighlightingAndConnectLSP
import com.rk.theme.greenStatus
import com.rk.theme.yellowStatus
import org.eclipse.lsp4j.ServerCapabilities
import java.io.File
import java.net.URI

abstract class ScriptedLspServer : LspServer() {
    abstract val installScript: File
    abstract val installId: String

    override fun install(activity: Activity) = launchInstaller(activity)

    override fun uninstall(activity: Activity) = launchInstaller(activity, "--uninstall")

    override fun update(activity: Activity) = launchInstaller(activity, "--update")

    protected fun launchInstaller(activity: Activity, vararg flags: String) {
        TerminalLauncher.launch(
            activity = activity,
            exe = "/bin/bash",
            args = arrayOf(installScript.absolutePath, *flags),
            id = installId,
            env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
        )
    }
}

abstract class LspServer {
    abstract val id: String
    abstract val languageName: String
    abstract val serverName: String
    abstract val supportedExtensions: List<String>
    abstract val icon: Icon?

    open val canBeUninstalled = true

    open val expectedCapabilities: ServerCapabilities? = null

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

    abstract fun install(activity: Activity)

    abstract fun uninstall(activity: Activity)

    abstract suspend fun hasUpdate(context: Context): Boolean

    @Deprecated("Rename to hasUpdate instead.", ReplaceWith("hasUpdate(context)"))
    open suspend fun isUpdatable(context: Context): Boolean {
        return hasUpdate(context)
    }

    abstract fun update(activity: Activity)

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
