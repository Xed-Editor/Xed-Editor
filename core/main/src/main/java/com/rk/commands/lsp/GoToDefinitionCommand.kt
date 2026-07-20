package com.rk.commands.lsp

import com.rk.DefaultScope
import com.rk.commands.LspActionContext
import com.rk.commands.LspCommand
import com.rk.commands.LspNonActionContext
import com.rk.icons.Icon
import com.rk.lsp.goToDefinition
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class GoToDefinitionCommand : LspCommand() {
    override val id: String = "lsp.go_to_definition"

    override fun getLabel(): String = strings.go_to_definition.getString()

    override fun action(context: LspActionContext) {
        goToDefinition(
            scope = DefaultScope,
            context = context.currentActivity,
            viewModel = commandContext.mainViewModel,
            editorTab = context.editorTab,
        )
    }

    override fun isSupported(context: LspNonActionContext): Boolean {
        return context.lspConnector.isGoToDefinitionSupported()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.jump_to_element)
}
