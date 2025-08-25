package com.rk.libcommons.editor

import com.rk.file.FileObject
import com.rk.libcommons.editor.textmateSources
import com.rk.libcommons.toast
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.lsp.client.connection.SocketStreamConnectionProvider
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent

class BaseLspConnector(
    private val ext: String,
    private val port: Int
) {
    private var project: LspProject? = null
    private var serverDefinition: CustomLanguageServerDefinition? = null
    private var lspEditor: LspEditor? = null

    fun isSupported(file: FileObject): Boolean {
        val fileExt = file.getName().substringAfterLast(".")
        return fileExt == ext && textmateSources.containsKey(fileExt)
    }

    suspend fun connect(projectFile: FileObject,fileObject: FileObject, karbonEditor: KarbonEditor) = withContext(Dispatchers.IO) {
        if (!isSupported(fileObject)) {
            return@withContext
        }
        runCatching {
            serverDefinition = object : CustomLanguageServerDefinition(ext, ServerConnectProvider {
                SocketStreamConnectionProvider(port)
            }) {}

            project = LspProject(projectFile.getAbsolutePath())

            project!!.addServerDefinition(serverDefinition!!)

            lspEditor = withContext(Dispatchers.Main) {
                project!!.createEditor(fileObject.getAbsolutePath()).apply {
                    wrapperLanguage = TextMateLanguage.create(textmateSources[ext], false)
                    editor = karbonEditor
                }
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
        }.onFailure {
            it.printStackTrace()
            toast("Failed to connect to lsp server ${it.message}")
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching {
            lspEditor?.dispose()
        }.onFailure { it.printStackTrace() }
    }
}