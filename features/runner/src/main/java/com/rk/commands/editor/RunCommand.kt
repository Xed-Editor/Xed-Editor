package com.rk.commands.editor

import android.view.KeyEvent
import com.rk.commands.CommandProvider
import com.rk.commands.EditorFileActionContext
import com.rk.commands.EditorFileCommand
import com.rk.commands.EditorFileNonActionContext
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.RunnerManager
import com.rk.runner.RunnerUI
import kotlinx.coroutines.DelicateCoroutinesApi

@OptIn(DelicateCoroutinesApi::class)
class RunCommand : EditorFileCommand() {
    override val id: String = "editor.run"

    override fun getLabel(): String = strings.run.getString()

    override fun action(context: EditorFileActionContext) {
        launchRunner(context, forceSelection = false)
    }

    override fun onLongClick(context: EditorFileActionContext): Boolean {
        launchRunner(context, forceSelection = true)
        return true
    }

    private fun launchRunner(context: EditorFileActionContext, forceSelection: Boolean) {
        RunnerManager.run(
            activity = context.currentActivity,
            fileObject = context.file,
            projectRoot = context.editorTab.projectRoot,
            forceSelection = forceSelection,
            beforeRun = {
                CommandProvider.SaveCommand.action(context)
            },
            onMultipleRunners = {
                RunnerUI.runnersToShow = it
                RunnerUI.showRunnerDialog = true
            },
        )
    }

    override fun isSupported(context: EditorFileNonActionContext): Boolean {
        return RunnerManager.isRunnable(context.file, context.editorTab.projectRoot)
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.run)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_F5)
}
