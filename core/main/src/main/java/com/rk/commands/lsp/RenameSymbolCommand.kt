package com.rk.commands.lsp

import com.rk.DefaultScope
import com.rk.commands.CommandContext
import com.rk.commands.LspActionContext
import com.rk.commands.LspCommand
import com.rk.commands.LspNonActionContext
import com.rk.icons.Icon
import com.rk.lsp.renameSymbol
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class RenameSymbolCommand(commandContext: CommandContext) : LspCommand(commandContext) {
    override val id: String = "lsp.rename_symbol"

    override fun getLabel(): String = strings.rename_symbol.getString()

    override fun action(lspActionContext: LspActionContext) {
        renameSymbol(DefaultScope, lspActionContext.editorTab)
    }

    override fun isSupported(lspNonActionContext: LspNonActionContext): Boolean {
        return lspNonActionContext.baseLspConnector.isRenameSymbolSupported()
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.manage_search)
}
