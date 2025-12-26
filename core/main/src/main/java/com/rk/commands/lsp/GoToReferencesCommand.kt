package com.rk.commands.lsp

import com.rk.DefaultScope
import com.rk.commands.CommandContext
import com.rk.commands.LspActionContext
import com.rk.commands.LspCommand
import com.rk.commands.LspNonActionContext
import com.rk.icons.Icon
import com.rk.lsp.goToReferences
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class GoToReferencesCommand(commandContext: CommandContext) : LspCommand(commandContext) {
    override val id: String = "lsp.go_to_references"

    override fun getLabel(): String = strings.go_to_references.getString()

    override fun action(lspActionContext: LspActionContext) {
        goToReferences(
            scope = DefaultScope,
            context = lspActionContext.currentActivity,
            viewModel = commandContext.mainViewModel,
            editorTab = lspActionContext.editorTab,
        )
    }

    override fun isSupported(lspNonActionContext: LspNonActionContext): Boolean {
        return lspNonActionContext.baseLspConnector.isGoToReferencesSupported()
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.manage_search)
}
