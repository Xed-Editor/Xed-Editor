package com.rk.lsp

import com.rk.editor.Editor
import com.rk.file.FileObject
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.dialog
import com.rk.utils.errorDialog
import com.rk.utils.info
import com.rk.utils.toast
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
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
import org.eclipse.lsp4j.ReferenceParams
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

            runCatching {
                    val projectPath = projectFile.getAbsolutePath()
                    val fileExt = fileObject.getName().substringAfterLast(".", "")

                    val project =
                        projectCache.computeIfAbsent(projectPath) { LspProject(projectFile.getAbsolutePath()) }

                    servers.forEach { server ->
                        val serverDef = createServerDefinition(fileExt, server)

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
                    } else {
                        launch { servers.forEach { it.beforeConnect() } }
                    }

                    lspEditor!!.connectWithTimeout()
                    lspEditor!!
                        .requestManager
                        .didChangeWorkspaceFolders(
                            DidChangeWorkspaceFoldersParams().apply {
                                event =
                                    WorkspaceFoldersChangeEvent().apply {
                                        added =
                                            listOf(
                                                WorkspaceFolder(projectFile.getAbsolutePath(), projectFile.getName())
                                            )
                                    }
                            }
                        )
                    lspEditor!!.openDocument()
                    launch { servers.forEach { it.connectionSuccess(this@BaseLspConnector) } }
                }
                .onFailure {
                    codeEditor.setLanguage(textMateScope)
                    it.printStackTrace()
                    toast(it.message)
                    launch { servers.forEach { server -> server.connectionFailure(it.message) } }
                }
        }

    private fun createServerDefinition(fileExt: String, server: BaseLspServer): CustomLanguageServerDefinition {
        val newDef =
            object :
                CustomLanguageServerDefinition(
                    ext = fileExt,
                    serverConnectProvider =
                        ServerConnectProvider { server.getConnectionConfig().providerFactory().create() },
                    name = server.serverName,
                    extensionsOverride = server.supportedExtensions,
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

                            override fun onLogMessage(messageParams: MessageParams?) {
                                if (messageParams == null) {
                                    return super.onLogMessage(messageParams)
                                }
                                info(messageParams.message)
                            }

                            override fun onShowMessage(messageParams: MessageParams?) {
                                if (messageParams == null) {
                                    return super.onShowMessage(messageParams)
                                }

                                when (messageParams.type) {
                                    MessageType.Error -> errorDialog(messageParams.message)
                                    MessageType.Warning ->
                                        dialog(title = strings.warning.getString(), msg = messageParams.message)

                                    MessageType.Info ->
                                        dialog(title = strings.info.getString(), msg = messageParams.message)

                                    MessageType.Log -> info(messageParams.message)
                                }
                            }
                        }
            }
        return newDef
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
        val referenceProvider = caps?.referencesProvider
        return referenceProvider?.left == true || referenceProvider?.right != null
    }

    @Throws(Exception::class)
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
        val renameProvider = caps?.renameProvider
        return renameProvider?.left == true || renameProvider?.right != null
    }

    @Throws(Exception::class)
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
        val renameProvider = caps?.renameProvider
        return renameProvider?.right?.prepareProvider == true
    }

    @Throws(Exception::class)
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
            }
            .onFailure { it.printStackTrace() }
    }
}
