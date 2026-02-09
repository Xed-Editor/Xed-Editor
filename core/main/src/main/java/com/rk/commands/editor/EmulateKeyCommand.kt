package com.rk.commands.editor

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.commands.Command
import com.rk.commands.CommandContext
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.ToggleableCommand
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.keybinds.KeyUtils

class ModifierState {
    var shift by mutableStateOf(false)
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
    var meta by mutableStateOf(false)

    fun toMetaState(): Int {
        var state = 0
        if (shift) state = state or KeyEvent.META_SHIFT_ON
        if (ctrl) state = state or KeyEvent.META_CTRL_ON
        if (alt) state = state or KeyEvent.META_ALT_ON
        if (meta) state = state or KeyEvent.META_META_ON
        return state
    }
}

data class MetaEvent(val metaKeyCode: Int, val keyCode: Int, val action: () -> Unit, val isOn: () -> Boolean)

object EmulateKeyCommandSection {
    const val META_KEY_SECTION = 0
    const val KEY_SECTION = 1
}

class EmulateKeyCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    val modifierState = ModifierState()

    override val id: String = "editor.emulate_key"

    override fun getLabel(): String = strings.emulate_editor_key.getString()

    override fun action(editorActionContext: EditorActionContext) {}

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.keyboard)

    override val childCommands: List<Command> by lazy {
        val keyEvents = KeyEvent::class.java.fields
        val metaEvents =
            listOf(
                MetaEvent(
                    metaKeyCode = KeyEvent.META_SHIFT_ON,
                    keyCode = KeyEvent.KEYCODE_SHIFT_LEFT,
                    action = { modifierState.shift = !modifierState.shift },
                    isOn = { modifierState.shift },
                ),
                MetaEvent(
                    metaKeyCode = KeyEvent.META_CTRL_ON,
                    keyCode = KeyEvent.KEYCODE_CTRL_LEFT,
                    action = { modifierState.ctrl = !modifierState.ctrl },
                    isOn = { modifierState.ctrl },
                ),
                MetaEvent(
                    metaKeyCode = KeyEvent.META_ALT_ON,
                    keyCode = KeyEvent.KEYCODE_ALT_LEFT,
                    action = { modifierState.alt = !modifierState.alt },
                    isOn = { modifierState.alt },
                ),
                MetaEvent(
                    metaKeyCode = KeyEvent.META_META_ON,
                    keyCode = KeyEvent.KEYCODE_META_LEFT,
                    action = { modifierState.meta = !modifierState.meta },
                    isOn = { modifierState.meta },
                ),
            )

        metaEvents.map { metaEvent ->
            val keyDisplayName = KeyUtils.getKeyDisplayName(metaEvent.metaKeyCode)
            val keyIcon = KeyUtils.getKeyIcon(metaEvent.keyCode)

            object : EditorCommand(commandContext), ToggleableCommand {
                override val id: String = "editor.emulate_key.${keyDisplayName.lowercase()}"

                override val preferText: Boolean = true

                override fun getLabel(): String = keyDisplayName

                override fun action(editorActionContext: EditorActionContext) {
                    val action = if (!isOn()) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                    val keyEvent = KeyEvent(action, metaEvent.keyCode)
                    editorActionContext.editor.dispatchKeyEvent(keyEvent)

                    metaEvent.action()
                }

                override fun getIcon(): Icon = keyIcon

                override fun isOn(): Boolean = metaEvent.isOn()

                override val sectionId: Int = EmulateKeyCommandSection.META_KEY_SECTION
            }
        } +
            keyEvents.mapNotNull {
                if (!it.name.startsWith("KEYCODE_")) return@mapNotNull null

                val keyCode = it.getInt(null)
                val keyName = it.name.removePrefix("KEYCODE_")
                val keyDisplayName = KeyUtils.getKeyDisplayName(keyCode)
                val keyIcon = KeyUtils.getKeyIcon(keyCode)

                // Modifier keys are handled above
                // To avoid confusion, do not show KEYCODE variants of modifier keys
                if (KeyUtils.isModifierKey(keyCode)) return@mapNotNull null

                object : EditorCommand(commandContext) {
                    override val id: String = "editor.emulate_key.${keyName.lowercase()}"

                    override fun getLabel(): String = keyDisplayName

                    override fun action(editorActionContext: EditorActionContext) {
                        val keyEvent = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, modifierState.toMetaState())
                        editorActionContext.editor.dispatchKeyEvent(keyEvent)
                    }

                    override fun getIcon(): Icon = keyIcon

                    override val sectionId: Int = EmulateKeyCommandSection.KEY_SECTION
                }
            }
    }

    override fun getChildSearchPlaceholder(): String = strings.select_key.getString()
}
