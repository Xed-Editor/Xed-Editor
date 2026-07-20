package com.rk.commands.lsp

import com.rk.DefaultScope
import com.rk.commands.LspActionContext
import com.rk.commands.LspCommand
import com.rk.commands.LspNonActionContext
import com.rk.icons.Icon
import com.rk.lsp.formatDocument
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class FormatDocumentCommand : LspCommand() {
    override val id: String = "lsp.format_document"

    override fun getLabel(): String = strings.format_document.getString()

    override fun action(context: LspActionContext) {
        formatDocument(DefaultScope, context.editorTab)
    }

    override fun isEnabled(context: LspNonActionContext): Boolean {
        return context.editorTab.editorState.editable
    }

    override fun isSupported(context: LspNonActionContext): Boolean {
        return context.lspConnector.isFormattingSupported()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.auto_fix)
}
