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
import com.rk.settings.Preference
import com.rk.tabs.editor.EditorTab
import com.rk.utils.dialog
import com.rk.utils.errorDialog
import com.rk.utils.info
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.lsp.client.languageserver.ServerStatus
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.client.languageserver.wrapper.EventHandler
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspEventManager
import io.github.rosemoe.sora.lsp.editor.LspProject
import io.github.rosemoe.sora.lsp.requests.Timeout
import io.github.rosemoe.sora.lsp.requests.Timeouts
import io.github.rosemoe.sora.widget.CodeEditor
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DefinitionOptions
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.DocumentFormattingOptions
import org.eclipse.lsp4j.DocumentRangeFormattingOptions
import org.eclipse.lsp4j.InitializeResult
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
import org.eclipse.lsp4j.ReferenceOptions
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.services.LanguageServer

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
        return lspEditor?.isConnected ?: false
    }

    suspend fun connect(textMateScope: String) =
        withContext(Dispatchers.IO) {
            if (servers.any { !it.isSupported(fileObject) }) {
                return@withContext
            }

            try {
                runCatching {
                        val projectPath = projectFile.getAbsolutePath()
                        val fileExt = fileObject.getName().substringAfterLast(".")

                        val project =
                            projectCache.computeIfAbsent(projectPath) { LspProject(projectFile.getAbsolutePath()) }

                        servers.forEach { server ->
                            val serverDef = createServerDefinition(fileExt, server, this@withContext)

                            try {
                                project.addServerDefinition(serverDef)
                            } catch (e: IllegalArgumentException) {
                                e.printStackTrace()
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

                        if (isConnected()) {
                            info("LSP server already connected skipping...")
                            return@withContext
                        }

                        editorTab.editorState.isConnectingLsp = true
                        launch { servers.forEach { it.beforeConnect() } }

                        lspEditor!!.connect()
                        lspEditor!!
                            .requestManager
                            .didChangeWorkspaceFolders(
                                DidChangeWorkspaceFoldersParams().apply {
                                    event =
                                        WorkspaceFoldersChangeEvent().apply {
                                            added =
                                                listOf(
                                                    WorkspaceFolder(
                                                        projectFile.getAbsolutePath(),
                                                        projectFile.getName(),
                                                    )
                                                )
                                        }
                                }
                            )
                        lspEditor!!.openDocument()
                    }
                    .onFailure {
                        codeEditor.setLanguage(textMateScope)
                        it.printStackTrace()
                    }
            } finally {
                editorTab.editorState.isConnectingLsp = false
            }
        }

    private fun createServerDefinition(
        fileExt: String,
        server: BaseLspServer,
        scope: CoroutineScope,
    ): CustomLanguageServerDefinition {
        return object :
            CustomLanguageServerDefinition(
                ext = fileExt,
                serverConnectProvider =
                    ServerConnectProvider { server.getConnectionConfig().providerFactory().create() },
                name = server.serverName,
                extensionsOverride = server.supportedExtensions,
                expectedCapabilitiesOverride =
                    ServerCapabilities().apply {
                        if (!Preference.getBoolean("lsp_${server.id}_hover", true)) {
                            hoverProvider = Either.forLeft(false)
                        }
                        if (!Preference.getBoolean("lsp_${server.id}_signature_help", true)) {
                            signatureHelpProvider = null
                        }
                        if (!Preference.getBoolean("lsp_${server.id}_inlay_hints", true)) {
                            inlayHintProvider = Either.forLeft(false)
                        }
                        if (!Preference.getBoolean("lsp_${server.id}_completion", true)) {
                            completionProvider = null
                        }
                        if (!Preference.getBoolean("lsp_${server.id}_diagnostics", true)) {
                            diagnosticProvider = null
                        }
                        if (!Preference.getBoolean("lsp_${server.id}_formatting", true)) {
                            documentFormattingProvider = Either.forLeft(false)
                            documentRangeFormattingProvider = Either.forLeft(false)
                            documentOnTypeFormattingProvider = null
                        }
                    },
            ) {

            override fun getInitializationOptions(uri: URI?): Any? {
                return server.getInitializationOptions(uri)
            }

            override val eventListener: EventHandler.EventListener
                get() =
                    object : EventHandler.EventListener {
                        override fun initialize(server: LanguageServer?, result: InitializeResult) {
                            super.initialize(server, result)
                        }

                        override fun onHandlerException(exception: Exception) {
                            scope.launch {
                                server.connectionFailure(
                                    exception.message
                                ) // TODO: Still runs many times (see https://github.com/Rosemoe/sora-editor/issues/777)
                            }
                            server.status = LspConnectionStatus.ERROR

                            exception.cause?.localizedMessage?.let { message ->
                                server.addLog(LspLogEntry(MessageType.Error, message))

                                scope.launch {
                                    val snackbarHost = snackbarHostStateRef.get() ?: return@launch
                                    val result =
                                        snackbarHost.showSnackbar(
                                            message = message,
                                            actionLabel = strings.view_logs.getString(),
                                            duration = SnackbarDuration.Short,
                                        )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        val activity = MainActivity.instance!!
                                        val intent = Intent(activity, SettingsActivity::class.java)
                                        intent.putExtra("route", "${SettingsRoutes.LspServerLogs.route}/${server.id}")
                                        activity.startActivity(intent)
                                    }
                                }
                            }
                            exception.localizedMessage?.let { message ->
                                server.addLog(LspLogEntry(MessageType.Error, message))
                            }
                        }

                        override fun onLogMessage(messageParams: MessageParams?) {
                            if (messageParams == null) {
                                return super.onLogMessage(messageParams)
                            }
                            info(messageParams.message)
                            server.addLog(messageParams)
                        }

                        override fun onShowMessage(messageParams: MessageParams?) {
                            if (messageParams == null) {
                                return super.onShowMessage(messageParams)
                            }

                            server.addLog(messageParams)
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
                            if (server.status == LspConnectionStatus.ERROR && newStatus != ServerStatus.STARTING) return

                            if (newStatus == ServerStatus.INITIALIZED) {
                                scope.launch { server.connectionSuccess(this@BaseLspConnector) }
                            }

                            val statusMessage =
                                when (newStatus) {
                                    ServerStatus.STARTING -> "Connecting to LSP server..."
                                    ServerStatus.INITIALIZED -> "LSP server initialized"
                                    ServerStatus.STARTED -> "Connected to LSP server successfully"
                                    ServerStatus.STOPPING -> "Disconnecting from LSP server..."
                                    ServerStatus.STOPPED -> "Disconnected from LSP server"
                                }
                            server.addLog(LspLogEntry(MessageType.Info, statusMessage))

                            server.status =
                                when (newStatus) {
                                    ServerStatus.INITIALIZED,
                                    ServerStatus.STARTED -> LspConnectionStatus.CONNECTED
                                    ServerStatus.STARTING -> LspConnectionStatus.CONNECTING
                                    ServerStatus.STOPPING,
                                    ServerStatus.STOPPED -> LspConnectionStatus.NOT_RUNNING
                                }
                        }
                    }
        }
    }

    fun getEventManager(): LspEventManager? {
        return lspEditor?.eventManager
    }

    fun getCapabilities(): ServerCapabilities? {
        return lspEditor?.languageServerWrapper?.getServerCapabilities()
    }

    fun isGoToDefinitionSupported(): Boolean {
        val caps = getCapabilities()
        val definitionProvider: Either<Boolean, DefinitionOptions>? = caps?.definitionProvider
        return definitionProvider?.left == true || definitionProvider?.right != null
    }

    suspend fun requestDefinition(editor: CodeEditor): Either<List<Location>, List<LocationLink>> {
        return withContext(Dispatchers.Default) {
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
        val referenceProvider: Either<Boolean, ReferenceOptions>? = caps?.referencesProvider
        return referenceProvider?.left == true || referenceProvider?.right != null
    }

    suspend fun requestReferences(editor: CodeEditor): List<Location?> {
        return withContext(Dispatchers.Default) {
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
        val renameProvider: Either<Boolean, RenameOptions>? = caps?.renameProvider
        return renameProvider?.left == true || renameProvider?.right != null
    }

    suspend fun requestRenameSymbol(editor: CodeEditor, newName: String): WorkspaceEdit {
        return withContext(Dispatchers.Default) {
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
        val renameProvider: Either<Boolean, RenameOptions>? = caps?.renameProvider
        return renameProvider?.right?.prepareProvider == true
    }

    suspend fun requestPrepareRenameSymbol(
        editor: CodeEditor
    ): Either3<Range?, PrepareRenameResult?, PrepareRenameDefaultBehavior?>? {
        return withContext(Dispatchers.Default) {
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
        val formattingProvider: Either<Boolean, DocumentFormattingOptions>? = caps?.documentFormattingProvider
        return formattingProvider?.left == true || formattingProvider?.right != null
    }

    fun isRangeFormattingSupported(): Boolean {
        val caps = getCapabilities()
        val rangeFormattingProvider: Either<Boolean, DocumentRangeFormattingOptions>? =
            caps?.documentRangeFormattingProvider
        return rangeFormattingProvider?.left == true || rangeFormattingProvider?.right != null
    }

    suspend fun notifySave(charset: Charset = Charsets.UTF_8) {
        lspEditor?.saveDocument()
    }

    suspend fun disconnect() {
        runCatching {
                lspEditor?.disposeAsync()
                lspEditor = null
                //                setStatus(LspConnectionStatus.NOT_RUNNING)
            }
            .onFailure {
                it.printStackTrace()
                //                it.localizedMessage?.let { message -> log(LspLogEntry(MessageType.Error, message)) }
                //                setStatus(LspConnectionStatus.ERROR)
            }
    }
}
