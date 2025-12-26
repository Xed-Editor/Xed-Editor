package com.rk.commands.editor

import android.view.KeyEvent
import com.rk.DefaultScope
import com.rk.commands.CommandContext
import com.rk.commands.CommandProvider
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.EditorNonActionContext
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.Runner
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
class RunCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.run"

    override fun getLabel(): String = strings.run.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val editorTab = editorActionContext.editorTab
        val activity = editorActionContext.currentActivity
        CommandProvider.SaveCommand.action(editorActionContext)
        DefaultScope.launch {
            Runner.run(
                context = activity,
                fileObject = editorTab.file,
                onMultipleRunners = {
                    editorTab.editorState.showRunnerDialog = true
                    editorTab.editorState.runnersToShow = it
                },
            )
        }
    }

    override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean {
        return Runner.isRunnable(editorNonActionContext.editorTab.file)
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.run)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_F5)
}
