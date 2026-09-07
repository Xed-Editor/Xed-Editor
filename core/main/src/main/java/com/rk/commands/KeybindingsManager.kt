package com.rk.commands

import android.view.KeyEvent
import com.rk.activities.main.MainActivity
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.keybinds.KeyUtils
import com.rk.utils.application
import io.github.rosemoe.sora.event.KeyBindingEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
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
    @Deprecated("This is now saved as a file.") private const val KEY_KEYBINDINGS = "keybindings"
    private val keybindingsFile = application!!.filesDir.resolve("keybindings.json")

    private val json = Json {
        ignoreUnknownKeys = true
        allowTrailingComma = true
    }
    private val serializer = MapSerializer(String.serializer(), KeyCombination.serializer())

    private val customKeybinds = mutableMapOf<String, KeyCombination>()

    private var _keybindMap: MutableMap<KeyCombination, String>? = null
    private val keybindMap: MutableMap<KeyCombination, String>
        get() {
            if (_keybindMap == null) {
                _keybindMap = buildKeybindMap()
            }

            return _keybindMap!!
        }

    fun invalidate() {
        _keybindMap = null
    }

    @Deprecated("This is temporary migration code.")
    fun migrate() {
        runCatching {
            val old = Preference.getString(KEY_KEYBINDINGS, "")
            if (old.isEmpty()) return

            val oldActions =
                json.decodeFromString(
                    ListSerializer(KeyAction.serializer()),
                    old,
                )

            val migrated = oldActions.associate {
                it.commandId to it.keyCombination
            }

            keybindingsFile.writeText(json.encodeToString(serializer, migrated))

            Preference.removeKey(KEY_KEYBINDINGS)
        }
    }

    fun saveKeybindings() {
        keybindingsFile.writeText(json.encodeToString(serializer, customKeybinds))
    }

    fun loadKeybindings() {
        if (!keybindingsFile.exists()) return

        val content = keybindingsFile.readText()
        if (content.isEmpty()) return

        customKeybinds.clear()
        customKeybinds.putAll(json.decodeFromString(serializer, content))
        invalidate()
    }

    fun conflictsWithExisting(keyCombination: KeyCombination, command: Command): Boolean {
        return keybindMap[keyCombination]?.let { it != command.id } ?: false
    }

    fun resetCustomKey(commandId: String) {
        customKeybinds.remove(commandId)
        saveKeybindings()
        invalidate()
    }

    fun resetAllKeys() {
        customKeybinds.clear()
        saveKeybindings()
        invalidate()
    }

    fun editCustomKey(commandId: String, keyCombination: KeyCombination) {
        customKeybinds[commandId] = keyCombination
        saveKeybindings()
        invalidate()
    }

    private fun buildKeybindMap(): MutableMap<KeyCombination, String> {
        val map = mutableMapOf<KeyCombination, String>()

        customKeybinds.forEach { (commandId, keyCombination) ->
            map[keyCombination] = commandId
        }

        for (command in CommandProvider.commandList) {
            if (command.id in customKeybinds) continue

            command.defaultKeybinds?.let {
                map[it] = command.id
            }
        }

        return map
    }

    fun getKeyCombinationForCommand(command: Command): KeyCombination? {
        return customKeybinds[command.id] ?: command.defaultKeybinds
    }

    fun handleGlobalEvent(event: KeyEvent, mainActivity: MainActivity): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val keyCombination = KeyCombination.fromEvent(event)
        val commandId = keybindMap[keyCombination] ?: return false
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
        val commandId = keybindMap[keyCombination] ?: return false
        val command = CommandProvider.getForId(commandId) ?: return false
        if (!command.isSupported() || !command.isEnabled()) return false

        // handleGlobalEvent will handle editor events
        if (command !is EditorCommand) return false

        command.performCommand(ActionContext(mainActivity))
        return true
    }
}

@Deprecated("Only used for migration from the old keybindings format.")
@Serializable
private data class KeyAction(
    val commandId: String,
    val keyCombination: KeyCombination,
)
