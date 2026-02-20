package com.rk.lsp

import android.content.Intent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import com.rk.activities.main.MainActivity
import com.rk.activities.main.snackbarHostStateRef
import com.rk.activities.settings.SettingsActivity
import com.rk.activities.settings.SettingsRoutes
import com.rk.editor.Editor
import com.rk.file.FileObject
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.tabs.editor.EditorTab
import com.rk.utils.dialog
import com.rk.utils.errorDialog
import com.rk.utils.info
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.lsp.client.languageserver.ServerStatus
import io.github.rosemoe.sora.lsp.client.languageserver.ShutdownReason
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.LanguageServerDefinition
import io.github.rosemoe.sora.lsp.client.languageserver.wrapper.EventHandler
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspEventManager
import io.github.rosemoe.sora.lsp.editor.LspProject
import io.github.rosemoe.sora.lsp.events.AsyncEventListener
import io.github.rosemoe.sora.lsp.requests.Timeout
import io.github.rosemoe.sora.lsp.requests.Timeouts
import io.github.rosemoe.sora.widget.CodeEditor
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3

/**
 * A utility object to temporarily prevent specific LSP servers from being used for a project.
 *
 * This is useful in scenarios where a server needs to be disabled on-demand without permanently removing its
 * configuration. For example, a user might want to temporarily stop a server that is causing issues.
 *
 * When a server is "prevented" via `register()`:
 * 1. It is added to a list of prevented servers for the given project.
 * 2. Its current [LanguageServerDefinition] is cached.
 * 3. The definition is then removed from the `LspProject`, effectively disabling it.
 *
 * `unregister()` reverses this process by restoring the cached definition to the project.
 */
object DefinitionPrevention {
    private val preventedServers = ConcurrentHashMap<LspProject, List<BaseLspServer>>()
    private val cachedDefinitions = ConcurrentHashMap<LspProject, Map<BaseLspServer, LanguageServerDefinition>>()

    fun register(project: LspProject, server: BaseLspServer) {
        preventedServers[project] = preventedServers[project]?.plus(server) ?: listOf(server)
        server.supportedExtensions.firstOrNull()?.let {
            val currentDefinition = project.getServerDefinition(it, server.serverName) ?: return@let
            cachedDefinitions[project] =
                cachedDefinitions[project]?.plus(server to currentDefinition) ?: mapOf(server to currentDefinition)
        }
        server.supportedExtensions.forEach { project.removeServerDefinition(it, server.serverName) }
    }

    fun unregister(project: LspProject, server: BaseLspServer) {
        preventedServers[project] = preventedServers[project]?.minus(server) ?: listOf()
        cachedDefinitions[project]?.get(server)?.let { project.addServerDefinition(it) }
        cachedDefinitions[project]?.minus(server)?.let { cachedDefinitions[project] = it }
    }

    fun isServerPrevented(project: LspProject, server: BaseLspServer): Boolean {
        return preventedServers[project]?.contains(server) ?: false
    }
}

class BaseLspConnector(
    private val projectFile: FileObject,
    private val fileObject: FileObject,
    private val codeEditor: Editor,
    private val editorTab: EditorTab,
    private val servers: List<BaseLspServer>,
) {
    var lspEditor: LspEditor? = null

    companion object {
        private val projectCache = ConcurrentHashMap<String, LspProject>()
    }

    fun isConnected(): Boolean {
        return lspEditor?.isConnected == true
    }

    suspend fun connect(textMateScope: String) =
        withContext(Dispatchers.IO) {
            if (isConnected()) {
                info("LSP servers already connected skipping...")
                return@withContext
            }

            editorTab.editorState.isConnectingLsp = true

            val projectPath = projectFile.getAbsolutePath()
            val fileExt = fileObject.getExtension()

            val project = projectCache.computeIfAbsent(projectPath) { LspProject(projectFile.getAbsolutePath()) }

            servers.forEach { server ->
                val isForceStopped = DefinitionPrevention.isServerPrevented(project, server)
                if (!isForceStopped && project.getServerDefinition(fileExt, server.serverName) == null) {
                    val serverDef = server.createServerDefinition(this@withContext, fileExt, project)
                    try {
                        project.addServerDefinition(serverDef)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            lspEditor =
                withContext(Dispatchers.Main) {
                    project.getOrCreateEditor(fileObject.getAbsolutePath()).apply {
                        wrapperLanguage = TextMateLanguage.create(textMateScope, false)
                        editor = codeEditor
                        isEnableInlayHint = true
                    }
                }

            launch { servers.forEach { it.beforeConnect() } }

            try {
                lspEditor!!.connectWithTimeout()
                lspEditor!!
                    .requestManager
                    .didChangeWorkspaceFolders(
                        DidChangeWorkspaceFoldersParams().apply {
                            event =
                                WorkspaceFoldersChangeEvent().apply {
                                    added = listOf(WorkspaceFolder(projectPath, projectFile.getName()))
                                }
                        }
                    )
                lspEditor!!.openDocument()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                editorTab.editorState.isConnectingLsp = false

                val failedConnections =
                    servers.filter { server ->
                        server.instances.any { instance ->
                            val isCrashed = instance.status == LspConnectionStatus.CRASHED
                            val isTimeout = instance.status == LspConnectionStatus.TIMEOUT
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
            }
        }

    private fun BaseLspServer.createServerDefinition(
        scope: CoroutineScope,
        fileExt: String,
        lspProject: LspProject,
    ): CustomLanguageServerDefinition {
        return object :
            CustomLanguageServerDefinition(
                ext = fileExt,
                serverConnectProvider = ServerConnectProvider { getConnectionConfig().providerFactory().create() },
                name = serverName,
                extensionsOverride = supportedExtensions,
                // expectedCapabilitiesOverride =
                //     ServerCapabilities().apply {
                //         if (!Preference.getBoolean("lsp_${id}_hover", true)) {
                //             hoverProvider = Either.forLeft(false)
                //         }
                //         if (!Preference.getBoolean("lsp_${id}_signature_help", true)) {
                //             signatureHelpProvider = null
                //         }
                //         if (!Preference.getBoolean("lsp_${id}_inlay_hints", true)) {
                //             inlayHintProvider = Either.forLeft(false)
                //         }
                //         if (!Preference.getBoolean("lsp_${id}_completion", true)) {
                //             completionProvider = null
                //         }
                //         if (!Preference.getBoolean("lsp_${id}_diagnostics", true)) {
                //             diagnosticProvider = null
                //         }
                //         if (!Preference.getBoolean("lsp_${id}_formatting", true)) {
                //             documentFormattingProvider = Either.forLeft(false)
                //             documentRangeFormattingProvider = Either.forLeft(false)
                //             documentOnTypeFormattingProvider = null
                //         }
                //     },
            ) {
            val instance =
                BaseLspServerInstance(
                        server = this@createServerDefinition,
                        lspProject = lspProject,
                        projectRoot = projectFile,
                    )
                    .also { addInstance(it) }

            override fun getInitializationOptions(uri: URI?): Any? =
                this@createServerDefinition.getInitializationOptions(uri)

            override fun callExitForLanguageServer(): Boolean = true

            override val eventListener: EventHandler.EventListener
                get() =
                    object : EventHandler.EventListener {
                        override fun onEventException(eventListener: AsyncEventListener, exception: Exception) {
                            instance.addLog(LspLogEntry(MessageType.Error, "Event ${eventListener.eventName} failed"))
                            exception.localizedMessage?.let { message ->
                                instance.addLog(LspLogEntry(MessageType.Error, message))
                            }
                        }

                        override fun onHandlerException(exception: Exception) {
                            exception.cause?.localizedMessage?.let { message ->
                                instance.addLog(LspLogEntry(MessageType.Error, message))
                            }
                            exception.localizedMessage?.let { message ->
                                instance.addLog(LspLogEntry(MessageType.Error, message))
                            }
                        }

                        override fun onLogMessage(messageParams: MessageParams?) {
                            if (messageParams == null) return
                            info(messageParams.message)
                            instance.addLog(messageParams)
                        }

                        override fun onShowMessage(messageParams: MessageParams?) {
                            if (messageParams == null) return
                            instance.addLog(messageParams)
                            when (messageParams.type) {
                                MessageType.Error -> errorDialog(messageParams.message)
                                MessageType.Warning ->
                                    dialog(title = strings.warning.getString(), msg = messageParams.message)

                                MessageType.Info ->
                                    dialog(title = strings.info.getString(), msg = messageParams.message)

                                MessageType.Log -> info(messageParams.message)
                            }
                        }

                        override fun onStatusChange(newStatus: ServerStatus, oldStatus: ServerStatus) {
                            if (newStatus == ServerStatus.INITIALIZED) {
                                scope.launch { onInitialize(this@BaseLspConnector) }
                            }

                            // TODO: Think about this
                            //                            if (newStatus is ServerStatus.STOPPED && newStatus.reason ==
                            // ShutdownReason.UNUSED) {
                            //                                removeInstance(instance)
                            //                                supportedExtensions.forEach {
                            // lspProject.removeServerDefinition(it, serverName) }
                            //                            }

                            if (newStatus == ServerStatus.STARTED) {
                                instance.startupTime = System.currentTimeMillis()
                            } else if (newStatus is ServerStatus.STOPPED) {
                                instance.startupTime = -1
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
                            instance.addLog(LspLogEntry(MessageType.Info, statusMessage))

                            if (
                                oldStatus is ServerStatus.STOPPED &&
                                    oldStatus.reason == ShutdownReason.RESTART &&
                                    newStatus is ServerStatus.STARTING
                            ) {
                                instance.status = LspConnectionStatus.RESTARTING
                                return
                            }

                            instance.status =
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
                        }
                    }
        }
    }

    fun getEventManager(): LspEventManager? {
        return lspEditor?.eventManager
    }

    fun getCapabilities(): ServerCapabilities? {
        return runCatching { lspEditor?.languageServerWrapper?.getServerCapabilities() }.getOrNull()
    }

    fun isGoToDefinitionSupported(): Boolean {
        val caps = getCapabilities()
        val definitionProvider = caps?.definitionProvider
        return definitionProvider?.left == true || definitionProvider?.right != null
    }

    @Throws(Exception::class)
    suspend fun requestDefinition(editor: CodeEditor): Either<List<Location>, List<LocationLink>> {
        return withContext(Dispatchers.IO) {
            lspEditor!!
                .languageServerWrapper
                .requestManager!!
                .definition(
                    DefinitionParams(
                        TextDocumentIdentifier(fileObject.getAbsolutePath()),
                        Position(editor.cursor.leftLine, editor.cursor.leftColumn),
                    )
                )!!
                .get(Timeout[Timeouts.EXECUTE_COMMAND].toLong(), TimeUnit.MILLISECONDS)
        }
    }

    fun isGoToReferencesSupported(): Boolean {
        val caps = getCapabilities()
        val referenceProvider = caps?.referencesProvider
        return referenceProvider?.left == true || referenceProvider?.right != null
    }

    @Throws(Exception::class)
    suspend fun requestReferences(editor: CodeEditor): List<Location?> {
        return withContext(Dispatchers.IO) {
            lspEditor!!
                .languageServerWrapper
                .requestManager!!
                .references(
                    ReferenceParams(
                        TextDocumentIdentifier(fileObject.getAbsolutePath()),
                        Position(editor.cursor.leftLine, editor.cursor.leftColumn),
                        ReferenceContext(true),
                    )
                )!!
                .get(Timeout[Timeouts.EXECUTE_COMMAND].toLong(), TimeUnit.MILLISECONDS)
        }
    }

    fun isRenameSymbolSupported(): Boolean {
        val caps = getCapabilities()
        val renameProvider = caps?.renameProvider
        return renameProvider?.left == true || renameProvider?.right != null
    }

    @Throws(Exception::class)
    suspend fun requestRenameSymbol(editor: CodeEditor, newName: String): WorkspaceEdit {
        return withContext(Dispatchers.IO) {
            lspEditor!!
                .languageServerWrapper
                .requestManager!!
                .rename(
                    RenameParams(
                        TextDocumentIdentifier(fileObject.getAbsolutePath()),
                        Position(editor.cursor.leftLine, editor.cursor.leftColumn),
                        newName,
                    )
                )!!
                .get(Timeout[Timeouts.EXECUTE_COMMAND].toLong(), TimeUnit.MILLISECONDS)
        }
    }

    fun isPrepareRenameSymbolSupported(): Boolean {
        val caps = getCapabilities()
        val renameProvider = caps?.renameProvider
        return renameProvider?.right?.prepareProvider == true
    }

    @Throws(Exception::class)
    suspend fun requestPrepareRenameSymbol(
        editor: CodeEditor
    ): Either3<Range?, PrepareRenameResult?, PrepareRenameDefaultBehavior?>? {
        return withContext(Dispatchers.IO) {
            lspEditor!!
                .languageServerWrapper
                .requestManager!!
                .prepareRename(
                    PrepareRenameParams(
                        TextDocumentIdentifier(fileObject.getAbsolutePath()),
                        Position(editor.cursor.leftLine, editor.cursor.leftColumn),
                    )
                )!!
                .get(Timeout[Timeouts.EXECUTE_COMMAND].toLong(), TimeUnit.MILLISECONDS)
        }
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

    suspend fun notifySave() {
        lspEditor?.saveDocument()
    }

    suspend fun disconnect() {
        runCatching {
                lspEditor?.disposeAsync()
                lspEditor = null

                //                val lspProject = projectCache[projectFile.getAbsolutePath()] ?: return@runCatching
                //                val instances =
                //                    servers
                //                        .mapNotNull { server ->
                //                            server.instances.find { instance -> instance.lspProject == lspProject }
                //                        }
                //                        .filter { instance ->
                //                            instance.status == LspConnectionStatus.NOT_RUNNING ||
                //                                instance.status == LspConnectionStatus.CRASHED ||
                //                                instance.status == LspConnectionStatus.TIMEOUT
                //                        }
                //                        .filter { instance ->
                //                            lspProject.getEditors().none {
                // instance.server.supportedExtensions.contains(it.fileExt) }
                //                        }
                //                instances.forEach { instance ->
                //                    DefinitionPrevention.unregister(lspProject, instance.server)
                //                    instance.server.removeInstance(instance)
                //                    instance.server.supportedExtensions.forEach {
                //                        lspProject.removeServerDefinition(it, instance.server.serverName)
                //                    }
                //                }
            }
            .onFailure { it.printStackTrace() }
    }
}
