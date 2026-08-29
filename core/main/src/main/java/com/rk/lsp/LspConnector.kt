package com.rk.lsp

import android.content.Intent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import com.google.gson.JsonParser
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.ui.snackbarHostStateRef
import com.rk.activities.settings.SettingsActivity
import com.rk.activities.settings.SettingsRoutes
import com.rk.editor.Editor
import com.rk.events.Events
import com.rk.events.LSPEvent
import com.rk.file.FileObject
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.tabs.editor.EditorTab
import com.rk.utils.logError
import com.rk.utils.logInfo
import com.rk.utils.showSnackbar
import com.rk.utils.toast
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.lsp.client.languageserver.LspFeature
import io.github.rosemoe.sora.lsp.client.languageserver.ServerStatus
import io.github.rosemoe.sora.lsp.client.languageserver.ShutdownReason
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.client.languageserver.wrapper.EventHandler
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspEventManager
import io.github.rosemoe.sora.lsp.editor.LspProject
import io.github.rosemoe.sora.lsp.events.AsyncEventListener
import io.github.rosemoe.sora.lsp.requests.Timeouts
import io.github.rosemoe.sora.text.CharPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class LspConnector(
    private val projectFile: FileObject,
    private val fileObject: FileObject,
    private val codeEditor: Editor,
    private val editorTab: EditorTab,
    private val servers: List<LspServer>,
) {
    var lspEditor: LspEditor? = null
        private set

    companion object {
        private val projectCache = ConcurrentHashMap<String, LspProject>()
    }

    fun isConnected(): Boolean {
        return lspEditor?.isConnected == true
    }

    suspend fun connect(wrapperLanguage: TextMateLanguage?) =
        withContext(Dispatchers.IO) {
            if (isConnected()) {
                logInfo("LSP servers already connected skipping...")
                return@withContext
            }

            editorTab.registerTask(EditorTab.LSP_CONNECTING_TASK_ID)

            val projectPath = projectFile.getAbsolutePath()
            val fileExt = fileObject.getExtension()

            val project = projectCache.computeIfAbsent(projectPath) { LspProject(projectPath) }

            servers.forEach { server ->
                val isForceStopped = DefinitionPrevention.isServerPrevented(project, server)
                if (!isForceStopped && project.getServerDefinition(fileExt, server.serverName) == null) {
                    val serverDef = server.createServerDefinition(this@withContext, fileExt, project)
                    try {
                        project.addServerDefinition(serverDef)
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            }

            lspEditor =
                withContext(Dispatchers.Main) {
                    project.getOrCreateEditor(fileObject.getAbsolutePath()).apply {
                        this.wrapperLanguage = wrapperLanguage
                        this.editor = codeEditor
                        this.isEnableInlayHint = true
                    }
                }

            launch { servers.forEach { it.beforeConnect() } }

            try {
                lspEditor?.connectWithTimeout()
            } catch (e: Exception) {
                logError(e)
            } finally {
                editorTab.unregisterTask(EditorTab.LSP_CONNECTING_TASK_ID)

                val failedConnections = servers.filter { server ->
                    server.instances.value.any { instance ->
                        val isCrashed = instance.status.value == LspConnectionStatus.CRASHED
                        val isTimeout = instance.status.value == LspConnectionStatus.TIMEOUT
                        instance.lspProject == project && (isCrashed || isTimeout)
                    }
                }

                if (failedConnections.isNotEmpty()) {
                    launch {
                        val snackbarHost = snackbarHostStateRef.get() ?: return@launch
                        val result =
                            snackbarHost.showSnackbar(
                                message = strings.lsp_connection_error.getString(),
                                actionLabel = strings.manage.getString(),
                                duration = SnackbarDuration.Short,
                            )
                        if (result == SnackbarResult.ActionPerformed) {
                            val activity = MainActivity.instance!!
                            val intent = Intent(activity, SettingsActivity::class.java)
                            intent.putExtra("route", SettingsRoutes.LspSettings.route)
                            activity.startActivity(intent)
                        }
                    }
                }

                Events.publish(
                    LSPEvent.ConnectionCompleted(
                        servers = servers,
                        failedServers = failedConnections,
                        lspProject = project,
                        fileObject = fileObject,
                        editorTab = editorTab,
                        editor = codeEditor,
                    )
                )
            }
        }

    private fun LspServer.createServerDefinition(
        scope: CoroutineScope,
        fileExt: String,
        lspProject: LspProject,
    ): CustomLanguageServerDefinition {

        val instance =
            LspServerInstance(
                    server = this@createServerDefinition,
                    lspProject = lspProject,
                    projectRoot = projectFile,
                )
                .also { addInstance(it) }

        return object :
            CustomLanguageServerDefinition(
                ext = fileExt,
                serverConnectProvider =
                    ServerConnectProvider { getConnectionConfig().providerFactory().create(instance) },
                name = serverName,
                extensionsOverride = supportedExtensions,
                expectedCapabilitiesOverride = expectedCapabilities,
            ) {

            override val disabledFeatures: Set<LspFeature>
                get() = buildSet {
                    if (!Preference.getBoolean("lsp_${id}_document_highlight", true)) {
                        add(LspFeature.DocumentHighlight)
                    }
                    if (!Preference.getBoolean("lsp_${id}_hover", true)) {
                        add(LspFeature.Hover)
                    }
                    if (!Preference.getBoolean("lsp_${id}_signature_help", true)) {
                        add(LspFeature.SignatureHelp)
                    }
                    if (!Preference.getBoolean("lsp_${id}_inlay_hints", true)) {
                        add(LspFeature.InlayHint)
                    }
                    if (!Preference.getBoolean("lsp_${id}_completion", true)) {
                        add(LspFeature.Completion)
                    }
                    if (!Preference.getBoolean("lsp_${id}_diagnostics", true)) {
                        add(LspFeature.Diagnostics)
                    }
                    if (!Preference.getBoolean("lsp_${id}_formatting", true)) {
                        add(LspFeature.Formatting)
                    }
                }

            override val customTimeouts: Map<Timeouts, Int>
                get() {
                    val timeouts = this@createServerDefinition.customTimeouts
                    val userTimeout =
                        Preference.getInt(
                            key = "lsp_${id}_startup_timeout",
                            default = timeouts[Timeouts.INIT] ?: Timeouts.INIT.defaultTimeout,
                        )
                    return timeouts + (Timeouts.INIT to userTimeout)
                }

            override fun getInitializationOptions(uri: URI?): Any? {
                val initOptions = this@createServerDefinition.getInitializationOptions(uri)
                val userOptions = Preference.getString("lsp_${id}_initialization_options") ?: return initOptions

                return runCatching { JsonParser.parseString(userOptions) }.getOrNull()
            }

            override fun callExitForLanguageServer(): Boolean = true

            override val eventListener: EventHandler.EventListener
                get() =
                    object : EventHandler.EventListener {
                        override fun onEventException(eventListener: AsyncEventListener, exception: Exception) {
                            instance.addLog(
                                LspLogEntry(
                                    MessageSource.Client,
                                    MessageType.Error,
                                    "Event ${eventListener.eventName} failed",
                                )
                            )
                            exception.localizedMessage?.let { message ->
                                instance.addLog(LspLogEntry(MessageSource.Client, MessageType.Error, message))
                            }
                        }

                        override fun onHandlerException(exception: Exception) {
                            exception.cause?.localizedMessage?.let { message ->
                                instance.addLog(LspLogEntry(MessageSource.Client, MessageType.Error, message))
                            }
                            exception.localizedMessage?.let { message ->
                                instance.addLog(LspLogEntry(MessageSource.Client, MessageType.Error, message))
                            }
                        }

                        override fun onLogMessage(messageParams: MessageParams) {
                            instance.addLog(messageParams)
                        }

                        override fun onShowMessage(messageParams: MessageParams) {
                            instance.addLog(messageParams)

                            when (messageParams.type) {
                                MessageType.Error -> showSnackbar(messageParams.message)
                                MessageType.Warning -> showSnackbar(messageParams.message)
                                MessageType.Info -> showSnackbar(messageParams.message)
                                MessageType.Log -> toast(messageParams.message)
                                MessageType.Debug -> {}
                            }
                        }

                        override fun onLogTrace(params: LogTraceParams) {
                            instance.addLog(params)
                        }

                        override fun onStatusChange(newStatus: ServerStatus, oldStatus: ServerStatus) {
                            if (newStatus == ServerStatus.INITIALIZED) {
                                scope.launch { onInitialize(this@LspConnector) }
                            }

                            if (newStatus == ServerStatus.STARTED) {
                                instance.setStartupTime(System.currentTimeMillis())
                            } else if (newStatus is ServerStatus.STOPPED) {
                                instance.setHasError(false)
                                instance.setStartupTime(-1)
                            }

                            val statusMessage =
                                when (newStatus) {
                                    ServerStatus.IDLE -> "LSP server is in idle state"
                                    ServerStatus.STARTING -> "Starting LSP server..."
                                    ServerStatus.INITIALIZED -> "LSP server initialized"
                                    ServerStatus.STARTED -> "Connected to LSP server successfully"
                                    is ServerStatus.STOPPING ->
                                        "Disconnecting from LSP server... (reason: ${newStatus.reason})"
                                    is ServerStatus.STOPPED ->
                                        "Disconnected from LSP server (reason: ${newStatus.reason})\n"
                                }
                            instance.addLog(LspLogEntry(MessageSource.Client, MessageType.Info, statusMessage))

                            if (
                                oldStatus is ServerStatus.STOPPED &&
                                    oldStatus.reason == ShutdownReason.RESTART &&
                                    newStatus is ServerStatus.STARTING
                            ) {
                                instance.setStatus(LspConnectionStatus.RESTARTING)
                                return
                            }

                            val oldConnectionStatus = instance.status.value
                            instance.setStatus(
                                when (newStatus) {
                                    ServerStatus.IDLE -> LspConnectionStatus.NOT_RUNNING
                                    ServerStatus.INITIALIZED -> LspConnectionStatus.RUNNING
                                    ServerStatus.STARTED,
                                    ServerStatus.STARTING -> LspConnectionStatus.STARTING
                                    is ServerStatus.STOPPING -> {
                                        when (newStatus.reason) {
                                            ShutdownReason.CRASH -> LspConnectionStatus.CRASHED
                                            ShutdownReason.TIMEOUT -> LspConnectionStatus.TIMEOUT
                                            ShutdownReason.RESTART -> LspConnectionStatus.RESTARTING
                                            else -> LspConnectionStatus.STOPPING
                                        }
                                    }
                                    is ServerStatus.STOPPED -> {
                                        when (newStatus.reason) {
                                            ShutdownReason.CRASH -> LspConnectionStatus.CRASHED
                                            ShutdownReason.TIMEOUT -> LspConnectionStatus.TIMEOUT
                                            ShutdownReason.RESTART -> LspConnectionStatus.RESTARTING
                                            else -> LspConnectionStatus.NOT_RUNNING
                                        }
                                    }
                                }
                            )

                            DefaultScope.launch {
                                Events.publish(
                                    LSPEvent.StatusChanged(instance, instance.status.value, oldConnectionStatus)
                                )
                            }
                        }
                    }
        }
    }

    fun getEventManager(): LspEventManager? {
        return lspEditor?.eventManager
    }

    fun getCapabilities(): ServerCapabilities? = lspEditor?.requestManager?.capabilities

    fun isGoToDefinitionSupported(): Boolean {
        val caps = getCapabilities()
        val definitionProvider = caps?.definitionProvider
        return definitionProvider?.left == true || definitionProvider?.right != null
    }

    fun isGoToReferencesSupported(): Boolean {
        val caps = getCapabilities()
        val referenceProvider = caps?.referencesProvider
        return referenceProvider?.left == true || referenceProvider?.right != null
    }

    fun isRenameSymbolSupported(): Boolean {
        val caps = getCapabilities()
        val renameProvider = caps?.renameProvider
        return renameProvider?.left == true || renameProvider?.right != null
    }

    fun isPrepareRenameSymbolSupported(): Boolean {
        val caps = getCapabilities()
        val renameProvider = caps?.renameProvider
        return renameProvider?.right?.prepareProvider == true
    }

    fun isFormattingSupported(): Boolean {
        val caps = getCapabilities()
        val formattingProvider = caps?.documentFormattingProvider
        return formattingProvider?.left == true || formattingProvider?.right != null
    }

    fun isRangeFormattingSupported(): Boolean {
        val caps = getCapabilities()
        val rangeFormattingProvider = caps?.documentRangeFormattingProvider
        return rangeFormattingProvider?.left == true || rangeFormattingProvider?.right != null
    }

    suspend fun requestDefinition(position: Position? = null): Either<List<Location>, List<LocationLink>> {
        return lspEditor?.requestDefinition(position) ?: Either.forLeft(emptyList())
    }

    suspend fun requestDefinition(position: CharPosition): Either<List<Location>, List<LocationLink>> {
        return lspEditor?.requestDefinition(position) ?: Either.forLeft(emptyList())
    }

    suspend fun requestReferences(
        position: Position? = null,
        includeDeclaration: Boolean = true,
    ): List<Location?> {
        return lspEditor?.requestReferences(position, includeDeclaration) ?: emptyList()
    }

    suspend fun requestReferences(
        position: CharPosition,
        includeDeclaration: Boolean = true,
    ): List<Location?> {
        return lspEditor?.requestReferences(position, includeDeclaration) ?: emptyList()
    }

    suspend fun requestPrepareRename(
        position: Position? = null
    ): Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>? {
        return lspEditor?.requestPrepareRename(position)
    }

    suspend fun requestPrepareRename(
        position: CharPosition
    ): Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>? {
        return lspEditor?.requestPrepareRename(position)
    }

    suspend fun requestRename(
        newName: String,
        position: Position? = null,
    ): WorkspaceEdit {
        return lspEditor?.requestRename(newName, position) ?: WorkspaceEdit()
    }

    suspend fun requestRename(
        newName: String,
        position: CharPosition,
    ): WorkspaceEdit {
        return lspEditor?.requestRename(newName, position) ?: WorkspaceEdit()
    }

    suspend fun setTrace(value: String) {
        lspEditor?.setTrace(value)
    }

    fun notify(method: String, parameter: Any?) {
        lspEditor?.notify(method, parameter)
    }

    fun request(method: String, parameter: Any?): CompletableFuture<*>? {
        return lspEditor?.request(method, parameter)
    }

    suspend fun notifySave() {
        lspEditor?.saveDocument()
    }

    suspend fun disconnect() {
        runCatching {
            lspEditor?.disposeAsync()
            lspEditor = null
        }
            .onFailure { logError(it) }
    }
}
