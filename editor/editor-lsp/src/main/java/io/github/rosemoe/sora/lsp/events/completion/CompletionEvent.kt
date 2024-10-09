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

package io.github.rosemoe.sora.lsp.events.completion

import android.util.Log
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.events.AsyncEventListener
import io.github.rosemoe.sora.lsp.events.EventContext
import io.github.rosemoe.sora.lsp.events.EventType
import io.github.rosemoe.sora.lsp.events.getByClass
import io.github.rosemoe.sora.lsp.utils.asLspPosition
import io.github.rosemoe.sora.lsp.utils.createCompletionParams
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.util.Logger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.await
import org.eclipse.lsp4j.CompletionContext
import org.eclipse.lsp4j.CompletionItem
import java.util.concurrent.CompletableFuture


class CompletionEvent : AsyncEventListener() {
    override val eventName: String = "textDocument/completion"

    private var future: CompletableFuture<List<CompletionItem>>? = null

    override suspend fun handleAsync(context: EventContext) {
        val editor = context.get<LspEditor>("lsp-editor")
        val position = context.getByClass<CharPosition>() ?: return

        val requestManager = editor.requestManager ?: return

        val future = requestManager
            .completion(
                editor.uri.createCompletionParams(
                    position.asLspPosition(),
                    CompletionContext().apply {
                        triggerCharacter = null
                    }
                )
            )?.thenApply {
                if (it == null) {
                    return@thenApply emptyList<CompletionItem>()
                }
                if (it.isLeft) {
                    return@thenApply it.left
                }
                if (it.isRight) {
                    return@thenApply it.right.items
                }
                emptyList()
            } ?: CompletableFuture.completedFuture(emptyList())

        this.future = future

        try {
            context.put("completion-items", future.await())
        } catch (e: Exception) {
            if (e !is TimeoutCancellationException) {
                Logger.instance(this.javaClass.name)
                    .e("Request completion failed", e)
            }

        }
    }

    override fun dispose() {
        future?.cancel(true)
        future = null
    }

}

val EventType.completion: String
    get() = "textDocument/completion"