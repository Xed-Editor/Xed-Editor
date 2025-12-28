package com.rk.commands.editor

import android.view.KeyEvent
import com.rk.commands.Command
import com.rk.commands.CommandContext
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.keybinds.KeyUtils

class EmulateKeyCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.emulate_key"

    override fun getLabel(): String = strings.emulate_editor_key.getString()

    override fun action(editorActionContext: EditorActionContext) {}

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.keyboard)

    override val childCommands: List<Command> by lazy {
        KeyEvent::class.java.fields.mapNotNull {
            if (!it.name.startsWith("KEYCODE_")) return@mapNotNull null

            val keyCode = it.getInt(null)
            val keyName = it.name.removePrefix("KEYCODE_")
            val keyDisplayName = KeyUtils.getKeyDisplayName(keyCode)
            val keyIcon = KeyUtils.getKeyIcon(keyCode)

            object : EditorCommand(commandContext) {
                override val id: String = "editor.emulate_key.${keyName.lowercase()}"

                override fun getLabel(): String = keyDisplayName

                override fun action(editorActionContext: EditorActionContext) {
                    editorActionContext.editor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                }

                override fun getIcon(): Icon = Icon.DrawableRes(keyIcon)
            }
        }
    }

    override fun getChildSearchPlaceholder(): String = strings.select_key.getString()
}
