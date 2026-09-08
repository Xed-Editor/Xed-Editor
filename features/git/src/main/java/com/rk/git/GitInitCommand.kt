package com.rk.git

import com.rk.commands.ActionContext
import com.rk.commands.GlobalCommand
import com.rk.filetree.FileTreeTab
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import java.io.File

object GitInitCommand : GlobalCommand() {
    override val id: String = "git.init"

    override fun getLabel(): String = strings.git_init.getString()

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.git)

    override fun isSupported(): Boolean {
        val drawerViewModel = commandContext.drawerViewModel
        val currentTab = drawerViewModel.currentDrawerTab
        return currentTab is FileTreeTab
    }

    override fun execute(context: ActionContext) {
        val drawerViewModel = commandContext.drawerViewModel
        val currentTab = drawerViewModel.currentDrawerTab as? FileTreeTab ?: return
        val rootPath = currentTab.root.getAbsolutePath()

        val gitViewModel = gitViewModel.get()
        gitViewModel?.initRepository(File(rootPath)) {
            gitViewModel.loadRepository(rootPath)
        }
    }
}
