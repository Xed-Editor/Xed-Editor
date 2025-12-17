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
    private val server: BaseLspServer,
) {
    var project: LspProject? = null
    var serverDefinition: CustomLanguageServerDefinition? = null
    var lspEditor: LspEditor? = null

    companion object {
        private val projectCache = ConcurrentHashMap<String, LspProject>()
        private val serverDefinitionCache =
            ConcurrentHashMap<String, ConcurrentHashMap<String, CustomLanguageServerDefinition>>()
    }

    fun isConnected(): Boolean {
        return lspEditor?.isConnected ?: false
    }

    suspend fun connect(textMateScope: String) =
        withContext(Dispatchers.IO) {
            if (!server.isSupported(fileObject)) {
                return@withContext
            }

            try {
                runCatching {
                        val projectPath = projectFile.getAbsolutePath()

                        project =
                            projectCache.computeIfAbsent(projectPath) { LspProject(projectFile.getAbsolutePath()) }

                        val projectServerDefinition =
                            serverDefinitionCache.computeIfAbsent(projectPath) { ConcurrentHashMap() }

                        val fileExt = fileObject.getName().substringAfterLast(".")
                        serverDefinition =
                            projectServerDefinition.computeIfAbsent(fileExt) {
                                val newDef =
                                    object :
                                        CustomLanguageServerDefinition(
                                            fileExt,
                                            ServerConnectProvider {
                                                server.getConnectionConfig().providerFactory().create()
                                            },
                                        ) {
                                        override fun getInitializationOptions(uri: URI?): Any? {
                                            return server.getInitializationOptions(uri)
                                        }

                                        override val eventListener: EventHandler.EventListener
                                            get() =
                                                object : EventHandler.EventListener {
                                                    override fun initialize(
                                                        server: LanguageServer?,
                                                        result: InitializeResult,
                                                    ) {
                                                        super.initialize(server, result)
                                                    }

                                                    override fun onHandlerException(exception: Exception) {
                                                        exception.cause?.localizedMessage?.let { message ->
                                                            server.addLog(LspLogEntry(MessageType.Error, message))
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
                                                                dialog(
                                                                    title = strings.warning.getString(),
                                                                    msg = messageParams.message,
                                                                )
                                                            MessageType.Info ->
                                                                dialog(
                                                                    title = strings.info.getString(),
                                                                    msg = messageParams.message,
                                                                )
                                                            MessageType.Log -> info(messageParams.message)
                                                        }
                                                    }
                                                }
                                    }

                                project!!.addServerDefinition(newDef)
                                newDef
                            }

                        lspEditor =
                            withContext(Dispatchers.Main) {
                                project!!.getOrCreateEditor(fileObject.getAbsolutePath()).apply {
                                    wrapperLanguage = TextMateLanguage.create(textMateScope, false)
                                    editor = codeEditor
                                    isEnableHover = Preference.getBoolean("lsp_${server.id}_hover", true)
                                    isEnableInlayHint = Preference.getBoolean("lsp_${server.id}_inlay_hints", true)
                                    isEnableSignatureHelp =
                                        Preference.getBoolean("lsp_${server.id}_signature_help", true)
                                }
                            }

                        if (isConnected()) {
                            server.addLog(LspLogEntry(MessageType.Info, "LSP server already connected skipping..."))
                            info("LSP server already connected skipping...")
                            return@withContext
                        }

                        editorTab.editorState.isConnectingLsp = true
                        server.addLog(LspLogEntry(MessageType.Info, "Connecting to LSP server..."))
                        server.status = LspConnectionStatus.CONNECTING
                        launch { server.beforeConnect() }
                        lspEditor!!.connectWithTimeout()
                        server.addLog(LspLogEntry(MessageType.Info, "Connected to LSP server successfully"))
                        lspEditor!!
                            .requestManager
                            ?.didChangeWorkspaceFolders(
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
                        server.status = LspConnectionStatus.CONNECTED
                        launch { server.connectionSuccess(this@BaseLspConnector) }
                    }
                    .onFailure {
                        codeEditor.setLanguage(textMateScope)
                        it.printStackTrace()
                        it.message?.let { message ->
                            launch {
                                val result =
                                    snackbarHostStateRef
                                        .get()
                                        ?.showSnackbar(
                                            message = message,
                                            actionLabel = strings.view_logs.getString(),
                                            duration = SnackbarDuration.Short,
                                        )
                                when (result) {
                                    SnackbarResult.ActionPerformed -> {
                                        val activity = MainActivity.instance!!
                                        val intent = Intent(activity, SettingsActivity::class.java)
                                        intent.putExtra("route", "${SettingsRoutes.LspServerLogs.route}/${server.id}")
                                        activity.startActivity(intent)
                                    }
                                    else -> {}
                                }
                            }
                        }

                        it.localizedMessage?.let { message -> server.addLog(LspLogEntry(MessageType.Error, message)) }
                        server.status = LspConnectionStatus.ERROR
                        launch { server.connectionFailure(it.message) }
                    }
            } finally {
                editorTab.editorState.isConnectingLsp = false
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
                server.status = LspConnectionStatus.NOT_RUNNING
            }
            .onFailure {
                it.printStackTrace()
                it.localizedMessage?.let { message -> server.addLog(LspLogEntry(MessageType.Error, message)) }
                server.status = LspConnectionStatus.ERROR
            }
    }
}
