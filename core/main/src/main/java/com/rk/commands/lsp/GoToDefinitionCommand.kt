package com.rk.commands.lsp

import com.rk.DefaultScope
import com.rk.commands.CommandContext
import com.rk.commands.LspActionContext
import com.rk.commands.LspCommand
import com.rk.commands.LspNonActionContext
import com.rk.icons.Icon
import com.rk.lsp.goToDefinition
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class GoToDefinitionCommand(commandContext: CommandContext) : LspCommand(commandContext) {
    override val id: String = "lsp.go_to_definition"

    override fun getLabel(): String = strings.go_to_definition.getString()

    override fun action(lspActionContext: LspActionContext) {
        goToDefinition(
            scope = DefaultScope,
            context = lspActionContext.currentActivity,
            viewModel = commandContext.mainViewModel,
            editorTab = lspActionContext.editorTab,
        )
    }

    override fun isSupported(lspNonActionContext: LspNonActionContext): Boolean {
        return lspNonActionContext.baseLspConnector.isGoToDefinitionSupported()
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.jump_to_element)
}
