package com.rk.commands

import android.view.KeyEvent
import com.google.gson.Gson
import com.rk.activities.main.MainActivity
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.keybinds.KeyUtils

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
        fun fromEvent(event: KeyEvent): KeyCombination =
            KeyCombination(
                keyCode = event.keyCode,
                ctrl = event.isCtrlPressed,
                alt = event.isAltPressed,
                shift = event.isShiftPressed,
            )

        fun fromEvent(event: androidx.compose.ui.input.key.KeyEvent): KeyCombination = fromEvent(event.nativeKeyEvent)
    }
}

object KeybindingsManager {
    private const val KEY_KEYBINDINGS = "keybindings"
    private val gson = Gson()
    private val customKeybinds = mutableListOf<KeyAction>()
    val keybindMap = mutableMapOf<KeyCombination, String>()

    fun saveKeybindings() {
        val json = gson.toJson(customKeybinds)
        Preference.setString(KEY_KEYBINDINGS, json)
    }

    fun loadKeybindings() {
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

    fun resetCustomKey(commandId: String) {
        customKeybinds.removeIf { it.commandId == commandId }
        saveKeybindings()
        generateKeybindMap()
    }

    fun resetAllKeys() {
        customKeybinds.clear()
        saveKeybindings()
        generateKeybindMap()
    }

    fun editCustomKey(keyAction: KeyAction) {
        val index = customKeybinds.indexOfFirst { it.commandId == keyAction.commandId }
        if (index != -1) {
            customKeybinds[index] = keyAction
        } else {
            customKeybinds.add(keyAction)
        }
        saveKeybindings()
        generateKeybindMap()
    }

    fun generateKeybindMap() {
        keybindMap.clear()

        // First add user's custom keybindings
        customKeybinds.forEach { keybind -> keybindMap[keybind.keyCombination] = keybind.commandId }
        val customCommandIds = customKeybinds.map { it.commandId }.toSet()

        // If no custom keybind is set, proceed with default keybindings
        for (command in CommandProvider.globalCommands) {
            if (customCommandIds.contains(command.id)) continue
            command.defaultKeybinds?.let { keybindMap[it] = command.id }
        }
    }

    fun getKeyCombinationForCommand(commandId: String): KeyCombination? {
        return keybindMap.entries.find { it.value == commandId }?.key
    }

    fun handleEvent(event: KeyEvent, mainActivity: MainActivity): Boolean {
        // TODO: Check isSupported and isEnabled before running
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val keyCombination = KeyCombination.fromEvent(event)
        val commandId = keybindMap[keyCombination] ?: return false
        val command = CommandProvider.getForId(commandId) ?: return false

        command.performCommand(ActionContext(mainActivity))
        return true
    }
}
