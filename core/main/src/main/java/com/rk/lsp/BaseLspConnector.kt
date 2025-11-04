package com.rk.lsp

import com.rk.file.FileObject
import com.rk.editor.Editor
import com.rk.file.FileType
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DefinitionOptions
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.DocumentFormattingOptions
import org.eclipse.lsp4j.DocumentRangeFormattingOptions
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
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit


class BaseLspConnector(
    private val projectFile: FileObject,
    private val fileObject: FileObject,
    private val codeEditor: Editor,
    private val server: BaseLspServer,
) {
    private var project: LspProject? = null
    private var serverDefinition: CustomLanguageServerDefinition? = null
    private var lspEditor: LspEditor? = null


    companion object {
        private val projectCache = ConcurrentHashMap<String, LspProject>()
        private val serverDefinitionCache =
            ConcurrentHashMap<String, ConcurrentHashMap<String, CustomLanguageServerDefinition>>()
    }

    fun isSupported(file: FileObject): Boolean {
        if (server.isSupported(file).not()){
            return false
        }
        val fileExt = file.getName().substringAfterLast(".")
        return fileExt == this.fileObject.getName().substringAfterLast(".") && FileType.knowsExtension(fileExt)
    }

    fun isConnected(): Boolean{
        return lspEditor?.isConnected ?: false
    }

    suspend fun connect(textMateScope: String) = withContext(Dispatchers.IO) {
        if (!isSupported(fileObject)) {
            return@withContext
        }

        runCatching {
            val projectPath = projectFile.getAbsolutePath()

            project = projectCache.computeIfAbsent(projectPath) {
                LspProject(projectFile.getAbsolutePath())
            }

            val projectServerDefinition = serverDefinitionCache.computeIfAbsent(projectPath) {
                ConcurrentHashMap()
            }

            val fileExt = fileObject.getName().substringAfterLast(".")
            serverDefinition = projectServerDefinition.computeIfAbsent(fileExt) {
                val newDef = object : CustomLanguageServerDefinition(fileExt, ServerConnectProvider {
                    server.getConnectionConfig().providerFactory().create()
                }) {
                    override fun getInitializationOptions(uri: URI?): Any? {
                        return server.getInitializationOptions(uri)
                    }

                    override val eventListener: EventHandler.EventListener
                        get() = object : EventHandler.EventListener{
                            override fun initialize(
                                server: LanguageServer?,
                                result: InitializeResult
                            ) {
                                super.initialize(server, result)
                            }

                            override fun onLogMessage(messageParams: MessageParams?) {
                                if (messageParams == null){
                                    return super.onLogMessage(messageParams)
                                }
                                info(messageParams.message)
                            }

                            override fun onShowMessage(messageParams: MessageParams?) {
                                if (messageParams == null) {
                                    return super.onShowMessage(messageParams)
                                }

                                when(messageParams.type){
                                    MessageType.Error -> errorDialog(messageParams.message)
                                    MessageType.Warning -> dialog(title = strings.warning.getString(),msg = messageParams.message)
                                    MessageType.Info -> dialog(title = strings.info.getString(),msg = messageParams.message)
                                    MessageType.Log -> info(messageParams.message)
                                }
                            }
                        }
                }

                project!!.addServerDefinition(newDef)
                newDef
            }


            lspEditor = withContext(Dispatchers.Main) {
                project!!.getOrCreateEditor(fileObject.getAbsolutePath()).apply {
                    wrapperLanguage = TextMateLanguage.create(textMateScope,false)
                    editor = codeEditor
                }
            }

            if (isConnected()){
                info("LSP server already connected skipping...")
                return@withContext
            }

            lspEditor!!.connectWithTimeout()
            lspEditor!!.requestManager?.didChangeWorkspaceFolders(
                DidChangeWorkspaceFoldersParams().apply {
                    event = WorkspaceFoldersChangeEvent().apply {
                        added = listOf(
                            WorkspaceFolder(
                                projectFile.getAbsolutePath(),
                                projectFile.getName()
                            )
                        )
                    }
                }
            )
            lspEditor!!.openDocument()
        }.onFailure {
            codeEditor.setLanguage(textMateScope)
            it.printStackTrace()
            toast(it.message)
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
            lspEditor!!.languageServerWrapper.requestManager!!.definition(
                DefinitionParams(
                    TextDocumentIdentifier(fileObject.getAbsolutePath()),
                    Position(editor.cursor.leftLine, editor.cursor.leftColumn)
                )
            )!!.get(Timeout[Timeouts.EXECUTE_COMMAND].toLong(), TimeUnit.MILLISECONDS)
        }
    }

    fun isGoToReferencesSupported(): Boolean {
        val caps = getCapabilities()
        val referenceProvider: Either<Boolean, ReferenceOptions>? = caps?.referencesProvider
        return referenceProvider?.left == true || referenceProvider?.right != null
    }

    suspend fun requestReferences(editor: CodeEditor): List<Location?> {
        return withContext(Dispatchers.Default) {
            lspEditor!!.languageServerWrapper.requestManager!!.references(
                ReferenceParams(
                    TextDocumentIdentifier(fileObject.getAbsolutePath()),
                    Position(editor.cursor.leftLine, editor.cursor.leftColumn),
                    ReferenceContext(true)
                )
            )!!.get(Timeout[Timeouts.EXECUTE_COMMAND].toLong(), TimeUnit.MILLISECONDS)
        }
    }

    fun isRenameSymbolSupported(): Boolean {
        val caps = getCapabilities()
        val renameProvider: Either<Boolean, RenameOptions>? = caps?.renameProvider
        return renameProvider?.left == true || renameProvider?.right != null
    }

    suspend fun requestRenameSymbol(editor: CodeEditor, newName: String): WorkspaceEdit {
        return withContext(Dispatchers.Default) {
            lspEditor!!.languageServerWrapper.requestManager!!.rename(
                RenameParams(
                    TextDocumentIdentifier(fileObject.getAbsolutePath()),
                    Position(editor.cursor.leftLine, editor.cursor.leftColumn),
                    newName
                )
            )!!.get(Timeout[Timeouts.EXECUTE_COMMAND].toLong(), TimeUnit.MILLISECONDS)
        }
    }

    fun isPrepareRenameSymbolSupported(): Boolean {
        val caps = getCapabilities()
        val renameProvider: Either<Boolean, RenameOptions>? = caps?.renameProvider
        return renameProvider?.right?.prepareProvider == true
    }

    suspend fun requestPrepareRenameSymbol(editor: CodeEditor): Either3<Range?, PrepareRenameResult?, PrepareRenameDefaultBehavior?>? {
        return withContext(Dispatchers.Default) {
            lspEditor!!.languageServerWrapper.requestManager!!.prepareRename(
                PrepareRenameParams(
                    TextDocumentIdentifier(fileObject.getAbsolutePath()),
                    Position(editor.cursor.leftLine, editor.cursor.leftColumn)
                )
            )!!.get(Timeout[Timeouts.EXECUTE_COMMAND].toLong(), TimeUnit.MILLISECONDS)
        }
    }

    fun isFormattingSupported(): Boolean {
        val caps = getCapabilities()
        val formattingProvider: Either<Boolean, DocumentFormattingOptions>? = caps?.documentFormattingProvider
        return formattingProvider?.left == true || formattingProvider?.right != null
    }

    fun isRangeFormattingSupported(): Boolean {
        val caps = getCapabilities()
        val rangeFormattingProvider: Either<Boolean, DocumentRangeFormattingOptions>? = caps?.documentRangeFormattingProvider
        return rangeFormattingProvider?.left == true || rangeFormattingProvider?.right != null
    }

    suspend fun notifySave(charset: Charset = Charsets.UTF_8) {
        lspEditor?.saveDocument()
    }

    suspend fun disconnect() {
        runCatching {
            lspEditor?.disposeAsync()
            lspEditor = null
        }.onFailure { it.printStackTrace() }
    }
}