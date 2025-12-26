package com.rk.commands.lsp

import com.rk.DefaultScope
import com.rk.commands.CommandContext
import com.rk.commands.LspActionContext
import com.rk.commands.LspCommand
import com.rk.commands.LspNonActionContext
import com.rk.icons.Icon
import com.rk.lsp.formatDocumentRange
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class FormatSelectionCommand(commandContext: CommandContext) : LspCommand(commandContext) {
    override val id: String = "lsp.format_selection"

    override fun getLabel(): String = strings.format_selection.getString()

    override fun action(lspActionContext: LspActionContext) {
        formatDocumentRange(DefaultScope, lspActionContext.editorTab)
    }

    override fun isSupported(lspNonActionContext: LspNonActionContext): Boolean {
        return lspNonActionContext.baseLspConnector.isRangeFormattingSupported()
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.auto_fix)
}
