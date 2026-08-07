package com.rk.commands.lsp

import com.rk.commands.LspActionContext
import com.rk.commands.LspCommand
import com.rk.commands.LspNonActionContext
import com.rk.icons.Icon
import com.rk.lsp.formatDocumentSuspend
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.launch

class FormatDocumentLspCommand : LspCommand() {
    override val id: String = "lsp.format_document"

    override fun getLabel(): String = strings.format_document_lsp.getString()

    override fun action(context: LspActionContext) {
        context.editorTab.scope.launch {
            formatDocumentSuspend(context.editorTab)
        }
    }

    override fun isEnabled(context: LspNonActionContext): Boolean {
        return context.editorTab.editorState.editable
    }

    override fun isSupported(context: LspNonActionContext): Boolean {
        return context.lspConnector.isFormattingSupported()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.auto_fix)
}
