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

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.events.EventContext
import io.github.rosemoe.sora.lsp.events.EventListener
import io.github.rosemoe.sora.lsp.events.EventType
import io.github.rosemoe.sora.lsp.utils.transformToEditorDiagnostics
import org.eclipse.lsp4j.Diagnostic


class PublishDiagnosticsEvent : EventListener {
    override val eventName: String = "editor/publishDiagnostics"

    override fun handle(context: EventContext) {

        val lspEditor = context.get<LspEditor>("lsp-editor")
        val originEditor = lspEditor.editor ?: return
        val data = context.get<List<Diagnostic>>("data")

        val diagnosticsContainer =
            originEditor.diagnostics ?: DiagnosticsContainer()

        diagnosticsContainer.reset()

        diagnosticsContainer.addDiagnostics(
            data.transformToEditorDiagnostics(originEditor)
        )

        originEditor.diagnostics = diagnosticsContainer
    }


}

val EventType.publishDiagnostics: String
    get() = "editor/publishDiagnostics"