package com.rk.commands.editor

import android.view.KeyEvent
import com.rk.DefaultScope
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.KeyCombination
import com.rk.events.EditorTabEvent
import com.rk.events.Events
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.tabs.editor.EditorTab
import com.rk.utils.dialogRes
import kotlinx.coroutines.launch

class RefreshCommand : EditorCommand() {
    override val id: String = "editor.refresh"

    override fun getLabel(): String = strings.refresh.getString()

    override fun action(context: EditorActionContext) {
        val currentTab = context.editorTab
        if (currentTab.editorState.isDirty) {
            dialogRes(
                activity = context.currentActivity,
                title = strings.attention.getString(),
                msg = strings.ask_refresh.getString(),
                okRes = strings.refresh,
                onCancel = {},
                onOk = {
                    currentTab.refresh()
                    publishEvent(currentTab)
                },
            )
        } else {
            currentTab.refresh()
            publishEvent(currentTab)
        }
    }

    private fun publishEvent(currentTab: EditorTab) {
        DefaultScope.launch {
            Events.publish(EditorTabEvent.Refreshed(currentTab))
        }
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.refresh)

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_R, ctrl = true, shift = true)
}
