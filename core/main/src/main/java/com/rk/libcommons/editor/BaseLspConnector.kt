package com.rk.libcommons.editor

import android.util.Log
import com.rk.file.FileObject
import com.rk.libcommons.editor.textmateSources
import com.rk.libcommons.toast
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.lsp.client.connection.SocketStreamConnectionProvider
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import java.nio.charset.Charset

class BaseLspConnector(
    private val ext: String,
    private val port: Int? = null,
    private val connectionProvider: StreamConnectionProvider = SocketStreamConnectionProvider(port!!),
) {

    private var project: LspProject? = null
    private var serverDefinition: CustomLanguageServerDefinition? = null
    private var lspEditor: LspEditor? = null
    var isConnected: Boolean = false
        private set
    private var fileObject: FileObject? = null

    fun isSupported(file: FileObject): Boolean {
        val fileExt = file.getName().substringAfterLast(".")
        return fileExt == ext && textmateSources.containsKey(fileExt)
    }


    suspend fun connect(projectFile: FileObject,fileObject: FileObject, karbonEditor: KarbonEditor) = withContext(Dispatchers.IO) {
        if (!isSupported(fileObject)) {
            return@withContext
        }

        this@BaseLspConnector.fileObject = fileObject

        runCatching {
            serverDefinition = object : CustomLanguageServerDefinition(ext, ServerConnectProvider {
                connectionProvider
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
            isConnected = false
            it.printStackTrace()
            toast("Failed to connect to lsp server ${it.message}")
        }
    }

    suspend fun notifySave(charset: Charset = Charsets.UTF_8) {
        lspEditor!!.saveDocument()
    }

    suspend fun disconnect(){
        runCatching {
            lspEditor?.disposeAsync()
            isConnected = false
            lspEditor = null
        }.onFailure { it.printStackTrace() }
    }
}