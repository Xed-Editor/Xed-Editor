package com.rk.commands.lsp

import com.rk.DefaultScope
import com.rk.commands.LspActionContext
import com.rk.commands.LspCommand
import com.rk.commands.LspNonActionContext
import com.rk.icons.Icon
import com.rk.lsp.formatDocumentRange
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class FormatSelectionCommand : LspCommand() {
    override val id: String = "lsp.format_selection"

    override fun getLabel(): String = strings.format_selection.getString()

    override fun action(context: LspActionContext) {
        formatDocumentRange(DefaultScope, context.editorTab)
    }

    override fun isEnabled(context: LspNonActionContext): Boolean {
        return context.editorTab.editorState.editable
    }

    override fun isSupported(context: LspNonActionContext): Boolean {
        return context.lspConnector.isRangeFormattingSupported()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.auto_fix)
}
