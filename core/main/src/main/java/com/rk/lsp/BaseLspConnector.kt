package com.rk.lsp

import com.rk.file.FileObject
import com.rk.editor.Editor
import com.rk.file.FileType
import com.rk.utils.toast
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
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

/**
 * Core connector for initializing LSP servers in a code editor.
 *
 * @param fileExtension The file extension this LSP handles (e.g., "kt", "py")
 * @param textMateScope TextMate grammar scope for syntax highlighting (e.g., "source.kotlin")
 * @param connectionConfig Configuration for how to connect to the LSP server
 */
class BaseLspConnector(
    private val fileExtension: String,
    private val textMateScope: String,
    private val connectionConfig: LspConnectionConfig
) {
    private var project: LspProject? = null
    private var serverDefinition: CustomLanguageServerDefinition? = null
    private var lspEditor: LspEditor? = null
    var isConnected: Boolean = false
        private set
    private var fileObject: FileObject? = null

    companion object {
        private val projectCache = ConcurrentHashMap<String, LspProject>()
        private val serverDefinitionCache =
            ConcurrentHashMap<String, ConcurrentHashMap<String, CustomLanguageServerDefinition>>()
    }

    fun isSupported(file: FileObject): Boolean {
        val fileExt = file.getName().substringAfterLast(".")
        return fileExt == fileExtension && FileType.hasFileExtension(fileExt)
    }

    suspend fun connect(
        projectFile: FileObject,
        fileObject: FileObject,
        codeEditor: Editor
    ) = withContext(Dispatchers.IO) {
        if (!isSupported(fileObject)) {
            return@withContext
        }

        while (!Editor.Companion.isInit && isActive) delay(5)
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

            serverDefinition = projectServerDefinition.computeIfAbsent(fileExtension) {
                val newDef = object : CustomLanguageServerDefinition(fileExtension, ServerConnectProvider {
                    connectionConfig.toFactory().create()
                }) {}

                project!!.addServerDefinition(newDef)
                newDef
            }

            lspEditor = withContext(Dispatchers.Main) {
                project!!.getOrCreateEditor(fileObject.getAbsolutePath()).apply {
                    wrapperLanguage = TextMateLanguage.create(textMateScope, false)
                    editor = codeEditor
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
            codeEditor.setLanguage(
                languageScopeName = textMateScope,
            )
            isConnected = false
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