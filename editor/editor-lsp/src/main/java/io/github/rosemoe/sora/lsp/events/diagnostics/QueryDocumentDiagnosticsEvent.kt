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

package io.github.rosemoe.sora.lsp.events.diagnostics

import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.events.AsyncEventListener
import io.github.rosemoe.sora.lsp.events.EventContext
import io.github.rosemoe.sora.lsp.events.EventType
import io.github.rosemoe.sora.lsp.utils.createDocumentDiagnosticParams
import kotlinx.coroutines.future.await
import org.eclipse.lsp4j.DocumentDiagnosticReport
import java.util.concurrent.CompletableFuture


class QueryDocumentDiagnosticsEvent : AsyncEventListener() {
    override val eventName = "textDocument/diagnostics"

    var future: CompletableFuture<Void>? = null

    override suspend fun handleAsync(context: EventContext) {
        val editor = context.get<LspEditor>("lsp-editor")

        val requestManager = editor.requestManager ?: return

        val future = requestManager
            .diagnostic(
                editor.uri.createDocumentDiagnosticParams()
            )
            .thenApply { either: DocumentDiagnosticReport ->
                if (either.isRelatedFullDocumentDiagnosticReport)
                    either.relatedFullDocumentDiagnosticReport
                        .relatedDocuments
                else
                    either.relatedUnchangedDocumentDiagnosticReport
                        .relatedDocuments
            }

        this.future = future.thenAccept { }

        val result = future.await()

        if (result.isEmpty()) {
            return
        }

        context.put("diagnostics", result)
    }

    override fun dispose() {
        future?.cancel(true);
        future = null;
    }


}

val EventType.queryDocumentDiagnostics: String
    get() = "textDocument/diagnostics"