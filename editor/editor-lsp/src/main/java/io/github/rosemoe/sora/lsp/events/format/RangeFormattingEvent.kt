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

package io.github.rosemoe.sora.lsp.events.format

import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.getOption
import io.github.rosemoe.sora.lsp.events.AsyncEventListener
import io.github.rosemoe.sora.lsp.events.EventContext
import io.github.rosemoe.sora.lsp.events.EventType
import io.github.rosemoe.sora.lsp.events.document.applyEdits
import io.github.rosemoe.sora.lsp.requests.Timeout
import io.github.rosemoe.sora.lsp.requests.Timeouts
import io.github.rosemoe.sora.lsp.utils.LSPException
import io.github.rosemoe.sora.lsp.utils.createTextDocumentIdentifier
import io.github.rosemoe.sora.lsp.utils.asLspRange
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.TextRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.TextEdit


class RangeFormattingEvent : AsyncEventListener() {
    override val eventName = "textDocument/rangeFormatting"

    override suspend fun handleAsync(context: EventContext) {
        val editor = context.get<LspEditor>("lsp-editor")

        val textRange = context.get<TextRange>("range")
        val content = context.get<Content>("text")

        val requestManager = editor.requestManager ?: return

        val formattingParams = DocumentRangeFormattingParams()

        formattingParams.options = editor.eventManager.getOption<FormattingOptions>()

        formattingParams.textDocument =
            editor.uri.createTextDocumentIdentifier()


        formattingParams.range = textRange.asLspRange()

        val formattingFuture = requestManager.rangeFormatting(formattingParams) ?: return

        try {
            val textEditList: List<TextEdit>

            withTimeout(Timeout[Timeouts.FORMATTING].toLong()) {
                textEditList = formattingFuture.await() ?: listOf()
            }

            withContext(Dispatchers.Main) {
                editor.eventManager.emit(EventType.applyEdits) {
                    put("edits", textEditList)
                    put("content", content)
                }
            }

        } catch (exception: Exception) {
            throw LSPException("Formatting code timeout", exception)
        }
    }

}

val EventType.rangeFormatting: String
    get() = "textDocument/rangeFormatting"