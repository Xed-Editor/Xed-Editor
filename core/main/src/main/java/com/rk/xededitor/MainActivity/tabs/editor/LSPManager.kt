package com.rk.xededitor.MainActivity.tabs.editor

import android.util.Log
import com.rk.file.FileObject
import com.rk.libcommons.dialog
import com.rk.libcommons.editor.textmateSources
import com.rk.libcommons.toast
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.lsp.client.connection.SocketStreamConnectionProvider
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.client.languageserver.wrapper.EventHandler
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import org.eclipse.lsp4j.services.LanguageServer

class LspConnector(
    private val ext: String,
    private val port: Int
) {
    private var project: LspProject? = null
    private var serverDefinition: CustomLanguageServerDefinition? = null
    private var lspEditor: LspEditor? = null
    private val TAG = this::class.java.simpleName

    fun isSupported(file: FileObject): Boolean {
        val fileExt = file.getName().substringAfterLast(".")
        return fileExt == ext && textmateSources.containsKey(fileExt)
    }

    suspend fun connect(projectFile: FileObject, editorFragment: EditorFragment) = withContext(Dispatchers.IO) {
        if (!isSupported(editorFragment.file!!)) {
            return@withContext
        }
        runCatching {
            serverDefinition = object : CustomLanguageServerDefinition(ext, ServerConnectProvider {
                SocketStreamConnectionProvider(port)
            }) {
                override val eventListener: EventHandler.EventListener get() {
                    return object : EventHandler.EventListener{
                        override fun initialize(
                            server: LanguageServer?,
                            result: InitializeResult
                        ) {
                            super.initialize(server, result)
                        }

                        override fun onHandlerException(exception: Exception) {
                            Log.e(TAG,"HandlerException | ${exception.message}",exception)
                        }

                        override fun onLogMessage(messageParams: MessageParams?) {
                            super.onLogMessage(messageParams)
                            if (messageParams != null){
                                Log.i(TAG,"${messageParams.type} : ${messageParams.message}")
                            }

                        }

                        override fun onShowMessage(messageParams: MessageParams?) {
                            super.onShowMessage(messageParams)
                            if (messageParams != null){
                                messageParams.type
                                dialog(title = "LSP (${messageParams.type})", msg = messageParams.message)
                            }

                        }
                    }
                }
            }

            project = LspProject(projectFile.getAbsolutePath())

            project!!.addServerDefinition(serverDefinition!!)

            lspEditor = withContext(Dispatchers.Main) {
                project!!.createEditor(editorFragment.file!!.getAbsolutePath()).apply {
                    wrapperLanguage = TextMateLanguage.create(textmateSources[ext], false)
                    editor = editorFragment.editor
                }
            }

            lspEditor!!.connectWithTimeout()
            lspEditor!!.requestManager?.didChangeWorkspaceFolders(
                DidChangeWorkspaceFoldersParams().apply {
                    event = WorkspaceFoldersChangeEvent().apply {
                        added = listOf(
                            WorkspaceFolder(
                                projectFile.toUri().toString(),
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
