package com.rk.commands

import android.view.KeyEvent
import com.google.gson.Gson
import com.rk.activities.main.MainActivity
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.keybinds.KeyUtils
import io.github.rosemoe.sora.event.KeyBindingEvent

data class KeyAction(val commandId: String, val keyCombination: KeyCombination)

data class KeyCombination(
    val keyCode: Int,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
) {
    fun getDisplayName(): String {
        return buildString {
            if (ctrl) append("${strings.ctrl.getString()}-")
            if (shift) append("${strings.shift.getString()}-")
            if (alt) append("${strings.alt.getString()}-")

            append(KeyUtils.getShortDisplayName(keyCode))
        }
    }

    companion object {
        // Native Android KeyEvent
        fun fromEvent(event: KeyEvent): KeyCombination =
            KeyCombination(
                keyCode = event.keyCode,
                ctrl = event.isCtrlPressed,
                alt = event.isAltPressed,
                shift = event.isShiftPressed,
            )

        // Compose KeyEvent
        fun fromEvent(event: androidx.compose.ui.input.key.KeyEvent): KeyCombination = fromEvent(event.nativeKeyEvent)

        // sora-editor KeyBindingEvent
        fun fromEvent(event: KeyBindingEvent): KeyCombination =
            KeyCombination(
                keyCode = event.keyCode,
                ctrl = event.isCtrlPressed,
                alt = event.isAltPressed,
                shift = event.isShiftPressed,
            )
    }
}

object KeybindingsManager {
    private const val KEY_KEYBINDINGS = "keybindings"
    private val gson = Gson()
    private val customKeybinds = mutableListOf<KeyAction>()
    val keybindMap = mutableMapOf<KeyCombination, String>()

    fun saveKeybindings() {
        synchronized(this) {
            val json = gson.toJson(customKeybinds)
            Preference.setString(KEY_KEYBINDINGS, json)
        }
    }

    fun loadKeybindings() {
        synchronized(this) {
            try {
                val json = Preference.getString(KEY_KEYBINDINGS, "")
                if (json.isEmpty()) return

                val type = Array<KeyAction>::class.java
                val loadedActions = gson.fromJson(json, type)
                customKeybinds.clear()
                customKeybinds.addAll(loadedActions)
            } finally {
                generateKeybindMap()
            }
        }
    }

    fun conflictsWithExisting(keyCombination: KeyCombination, command: Command): Boolean {
        return synchronized(this) {
            keybindMap.containsKey(keyCombination) && keybindMap[keyCombination] != command.id
        }
    }

    fun resetCustomKey(commandId: String) {
        synchronized(this) {
            customKeybinds.removeIf { it.commandId == commandId }
            saveKeybindings()
            generateKeybindMap()
        }
    }

    fun resetAllKeys() {
        synchronized(this) {
            customKeybinds.clear()
            saveKeybindings()
            generateKeybindMap()
        }
    }

    fun editCustomKey(keyAction: KeyAction) {
        synchronized(this) {
            val index = customKeybinds.indexOfFirst { it.commandId == keyAction.commandId }
            if (index != -1) {
                customKeybinds[index] = keyAction
            } else {
                customKeybinds.add(keyAction)
            }
            saveKeybindings()
            generateKeybindMap()
        }
    }

    fun generateKeybindMap() {
        keybindMap.clear()

        customKeybinds.forEach { keybind -> keybindMap[keybind.keyCombination] = keybind.commandId }
        val customCommandIds = customKeybinds.map { it.commandId }.toSet()

        for (command in CommandProvider.commandList) {
            if (customCommandIds.contains(command.id)) continue
            command.defaultKeybinds?.let { keybindMap[it] = command.id }
        }
    }

    fun getKeyCombinationForCommand(commandId: String): KeyCombination? {
        return synchronized(this) { keybindMap.entries.find { it.value == commandId }?.key }
    }

    fun handleGlobalEvent(event: KeyEvent, mainActivity: MainActivity): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val keyCombination = KeyCombination.fromEvent(event)
        val localMap = synchronized(this) { HashMap(keybindMap) }
        val commandId = localMap[keyCombination] ?: return false
        val command = CommandProvider.getForId(commandId) ?: return false
        if (!command.isSupported() || !command.isEnabled()) return false

        // handleEditorEvent will handle editor events
        if (command is EditorCommand) return false

        command.performCommand(ActionContext(mainActivity))
        return true
    }

    fun handleEditorEvent(event: KeyBindingEvent, mainActivity: MainActivity): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val keyCombination = KeyCombination.fromEvent(event)
        val localMap = synchronized(this) { HashMap(keybindMap) }
        val commandId = localMap[keyCombination] ?: return false
        val command = CommandProvider.getForId(commandId) ?: return false
        if (!command.isSupported() || !command.isEnabled()) return false

        // handleGlobalEvent will handle editor events
        if (command !is EditorCommand) return false

        command.performCommand(ActionContext(mainActivity))
        return true
    }
}
