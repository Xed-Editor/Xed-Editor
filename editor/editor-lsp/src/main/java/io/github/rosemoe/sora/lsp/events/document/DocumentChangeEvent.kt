/*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2023  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 ******************************************************************************/

package io.github.rosemoe.sora.lsp.events.document

import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.events.AsyncEventListener
import io.github.rosemoe.sora.lsp.events.EventContext
import io.github.rosemoe.sora.lsp.events.EventType
import io.github.rosemoe.sora.lsp.events.getByClass
import io.github.rosemoe.sora.lsp.utils.createDidChangeTextDocumentParams
import io.github.rosemoe.sora.lsp.utils.createRange
import io.github.rosemoe.sora.lsp.utils.createTextDocumentContentChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentSyncKind
import java.util.concurrent.CompletableFuture

class DocumentChangeEvent : AsyncEventListener() {
    override val eventName = "textDocument/didChange"

    var future: CompletableFuture<Void>? = null

    override suspend fun handleAsync(context: EventContext) {
        val editor = context.get<LspEditor>("lsp-editor")
        val event = context.getByClass<ContentChangeEvent>() ?: return

        val params = createDidChangeTextDocumentParams(editor, event)

        editor.requestManager?.let { requestManager ->
            future = CompletableFuture.runAsync {
                requestManager.didChange(
                    params
                )
            }

            withContext(Dispatchers.IO) {
                future?.get()
            }

        }
    }

    override fun dispose() {
        future?.cancel(true);
        future = null;
    }

    private fun createFullTextDocumentContentChangeEvent(editor: LspEditor): List<TextDocumentContentChangeEvent> {
        return listOf(
            editor.uri.createTextDocumentContentChangeEvent(editor.editorContent)
        )
    }

    private fun createIncrementTextDocumentContentChangeEvent(
        editor: LspEditor,
        data: ContentChangeEvent
    ): List<TextDocumentContentChangeEvent> {
        val text = data.changedText.toString()
        return listOf(
            editor.uri.createTextDocumentContentChangeEvent(
                createRange(
                    data.changeStart,
                    data.changeEnd
                ),
                if (data.action == ContentChangeEvent.ACTION_DELETE) "" else text
            )
        )
    }


    private fun createDidChangeTextDocumentParams(
        editor: LspEditor,
        data: ContentChangeEvent
    ): DidChangeTextDocumentParams {
        val kind = editor.textDocumentSyncKind
        val isFullSync = kind == TextDocumentSyncKind.None || kind == TextDocumentSyncKind.Full
        println(isFullSync)

        return editor.uri.createDidChangeTextDocumentParams(
            if (isFullSync) createFullTextDocumentContentChangeEvent(editor) else createIncrementTextDocumentContentChangeEvent(
                editor,
                data
            )
        )
    }
}

val EventType.documentChange: String
    get() = "textDocument/didChange"