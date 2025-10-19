package com.rk.libcommons.editor

import com.rk.file.FileObject
import com.rk.libcommons.editor.KarbonEditor.Companion.isInit
import com.rk.libcommons.toast
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.lsp.client.connection.SocketStreamConnectionProvider
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspEventManager
import io.github.rosemoe.sora.lsp.editor.LspProject
import io.github.rosemoe.sora.lsp.requests.Timeout
import io.github.rosemoe.sora.lsp.requests.Timeouts
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DefinitionOptions
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
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
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class BaseLspConnector(
    private val ext: String,
    private val textMateScope: String,
    private val port: Int? = null,
    private val connectionProvider: StreamConnectionProvider = SocketStreamConnectionProvider(port!!),
) {

    private var project: LspProject? = null
    private var serverDefinition: CustomLanguageServerDefinition? = null
    private var lspEditor: LspEditor? = null
    var isConnected: Boolean = false
        private set
    private var fileObject: FileObject? = null

    companion object {
        private val projectCache = ConcurrentHashMap<String, LspProject>()
        private val serverDefinitionCache = ConcurrentHashMap<String, ConcurrentHashMap<String, CustomLanguageServerDefinition>>()
    }

    fun isSupported(file: FileObject): Boolean {
        val fileExt = file.getName().substringAfterLast(".")
        return fileExt == ext && textmateSources.containsKey(fileExt)
    }

    suspend fun connect(
        projectFile: FileObject,
        fileObject: FileObject,
        karbonEditor: KarbonEditor
    ) = withContext(Dispatchers.IO) {
        if (!isSupported(fileObject)) {
            return@withContext
        }

        while (!isInit && isActive) delay(5)
        if (!isActive) {
            return@withContext
        }

        this@BaseLspConnector.fileObject = fileObject

        runCatching {
            val projectPath = projectFile.getAbsolutePath()

            project = projectCache.computeIfAbsent(projectPath) {
                LspProject(projectFile.getAbsolutePath())
            }

            val projectServerDefinition = serverDefinitionCache.computeIfAbsent(projectPath) {
                ConcurrentHashMap()
            }

            serverDefinition = projectServerDefinition.computeIfAbsent(ext) {
                val newDef = object : CustomLanguageServerDefinition(ext, ServerConnectProvider {
                    connectionProvider
                }) {}

                project!!.addServerDefinition(newDef)
                newDef
            }

            lspEditor = withContext(Dispatchers.Main) {
                project!!.createEditor(fileObject.getAbsolutePath()).apply {
                    wrapperLanguage = TextMateLanguage.create(textMateScope, false)
                    editor = karbonEditor
                }
            }

            lspEditor!!.connectWithTimeout()
            isConnected = true
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
            karbonEditor.setLanguage(
                languageScopeName = textMateScope,
            )
            isConnected = false
            it.printStackTrace()
            toast("Failed to connect to lsp server ${it.message}")
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
                    TextDocumentIdentifier(fileObject!!.getAbsolutePath()),
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
                    TextDocumentIdentifier(fileObject!!.getAbsolutePath()),
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
                    TextDocumentIdentifier(fileObject!!.getAbsolutePath()),
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
                    TextDocumentIdentifier(fileObject!!.getAbsolutePath()),
                    Position(editor.cursor.leftLine, editor.cursor.leftColumn)
                )
            )!!.get(Timeout[Timeouts.EXECUTE_COMMAND].toLong(), TimeUnit.MILLISECONDS)
        }
    }

    suspend fun notifySave(charset: Charset = Charsets.UTF_8) {
        lspEditor!!.saveDocument()
    }

    suspend fun disconnect() {
        runCatching {
            lspEditor?.disposeAsync()
            isConnected = false
            lspEditor = null
        }.onFailure { it.printStackTrace() }
    }
}