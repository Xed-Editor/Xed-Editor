package com.rk.ai.command

import com.rk.activities.main.MainActivity
import com.rk.ai.icons.SparklesTiny
import com.rk.ai.tab.AiTab
import com.rk.commands.ActionContext
import com.rk.commands.GlobalCommand
import com.rk.icons.Icon
import com.rk.resources.getString
import com.rk.resources.strings

class AIChatCommand : GlobalCommand() {
    override val id: String = "global.ai_chat"

    override fun getLabel(): String = strings.ai_chat_command_label.getString()

    override fun execute(context: ActionContext) {
        MainActivity.instance?.viewModel?.tabManager?.addTab(AiTab(), switchToTab = true)
    }

    override fun getIcon(): Icon = Icon.VectorIcon(SparklesTiny)

    override fun isSupported(): Boolean {
        return MainActivity.instance?.viewModel?.currentVisibleTab !is AiTab
    }
}
