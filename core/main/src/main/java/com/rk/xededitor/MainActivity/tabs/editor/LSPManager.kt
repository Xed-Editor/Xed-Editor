package com.rk.xededitor.MainActivity.tabs.editor

import com.rk.file_wrapper.FileObject
import com.rk.file_wrapper.FileWrapper
import com.rk.libcommons.editor.SetupEditor
import com.rk.libcommons.editor.textmateSources
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.lsp.client.connection.SocketStreamConnectionProvider
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspProject
import io.github.rosemoe.sora.lsp.utils.FileUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent

fun isLspSupported(fileObject: FileObject): Boolean{
    return false && fileObject is FileWrapper && textmateSources.get(fileObject.getName().substringAfterLast(".")) != null
}

suspend fun connectLsp(port: Int,project: FileObject,editorFragment: EditorFragment) = withContext(
    Dispatchers.IO){

    if (isLspSupported(editorFragment.file!!).not()){
        println("LSP not supported")
        return@withContext
    }

    val ext = editorFragment.file!!.getName().substringAfterLast(".")
    val def = object : CustomLanguageServerDefinition(ext, ServerConnectProvider{
        SocketStreamConnectionProvider(port)
    }){}

    val lspProject = LspProject(project.getAbsolutePath())
    lspProject.addServerDefinition(def)

    val lspEditor = withContext(Dispatchers.Main){
        val lspEditorx = lspProject.createEditor(editorFragment.file!!.getAbsolutePath())
        val wrapperLanguage = TextMateLanguage.create(
            textmateSources.get(ext), false
        )
        lspEditorx.wrapperLanguage = wrapperLanguage
        lspEditorx.editor = editorFragment.editor
        lspEditorx
    }

    runCatching {
        lspEditor.connectWithTimeout()
        lspEditor.requestManager?.didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams().apply {
            this.event = WorkspaceFoldersChangeEvent().apply {
                added =
                    listOf(WorkspaceFolder(project.toUri().toString(),project.getName()))
            }
        })

    }.onFailure {
        it.printStackTrace()
    }






}