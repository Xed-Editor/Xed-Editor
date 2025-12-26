package com.rk.commands.editor

import android.view.KeyEvent
import com.rk.commands.CommandContext
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.dialog

class RefreshCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.refresh"

    override fun getLabel(): String = strings.refresh.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val currentTab = editorActionContext.editorTab
        if (currentTab.editorState.isDirty) {
            dialog(
                context = editorActionContext.currentActivity,
                title = strings.attention.getString(),
                msg = strings.ask_refresh.getString(),
                okString = strings.refresh,
                onCancel = {},
                onOk = { currentTab.refresh() },
            )
        } else {
            currentTab.refresh()
        }
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.refresh)

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_R, ctrl = true, shift = true)
}
