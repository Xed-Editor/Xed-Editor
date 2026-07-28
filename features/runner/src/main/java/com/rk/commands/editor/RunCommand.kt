package com.rk.commands.editor

import android.view.KeyEvent
import com.rk.commands.ActionContext
import com.rk.commands.Command
import com.rk.commands.CommandProvider
import com.rk.commands.KeyCombination
import com.rk.filetree.FileTreeTab
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.RunnerManager
import com.rk.runner.RunnerUI
import com.rk.tabs.editor.EditorTab
import kotlinx.coroutines.DelicateCoroutinesApi

@OptIn(DelicateCoroutinesApi::class)
object RunCommand : Command() {
    override val id: String = "editor.run"

    override fun getLabel(): String = strings.run.getString()

    override fun action(context: ActionContext) {
        launchRunner(context, forceSelection = false)
    }

    override fun onLongClick(context: ActionContext): Boolean {
        launchRunner(context, forceSelection = true)
        return true
    }

    private fun launchRunner(context: ActionContext, forceSelection: Boolean) {
        val mainViewModel = commandContext.mainViewModel
        val drawerViewModel = commandContext.drawerViewModel

        val currentTab = mainViewModel.currentTab as? EditorTab
        val currentDrawerTab = drawerViewModel.currentDrawerTab as? FileTreeTab
        val projectRoot = currentTab?.projectRoot ?: currentDrawerTab?.root
        val fileObject = currentTab?.file

        RunnerManager.run(
            activity = context.currentActivity,
            fileObject = fileObject,
            projectRoot = projectRoot,
            forceSelection = forceSelection,
            beforeRun = {
                if (currentTab != null) {
                    CommandProvider.SaveCommand.action(context)
                }
            },
            onMultipleRunners = {
                RunnerUI.runnersToShow = it
                RunnerUI.showRunnerDialog = true
            },
        )
    }

    override fun isSupported(): Boolean {
        val mainViewModel = commandContext.mainViewModel
        val drawerViewModel = commandContext.drawerViewModel

        val currentTab = mainViewModel.currentTab as? EditorTab
        val currentDrawerTab = drawerViewModel.currentDrawerTab as? FileTreeTab
        val projectRoot = currentTab?.projectRoot ?: currentDrawerTab?.root
        val fileObject = currentTab?.file

        return RunnerManager.isRunnable(fileObject, projectRoot)
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.run)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_F5)
}
