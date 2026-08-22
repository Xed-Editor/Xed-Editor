package com.rk.commands

import com.rk.extension.api.XedExtensionPoint
import com.rk.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ToolbarConfiguration {
    const val DEFAULT_EDITOR_TOOLBAR_COMMANDS =
        "editor.undo|editor.redo|editor.save|editor.run|global.new_file|editor.editable|editor.search|editor.refresh|global.terminal|global.settings"

    val editorCommands: List<Command>
        get() = Settings.action_items.split("|").mapNotNull { CommandProvider.getForId(it) }

    @XedExtensionPoint
    fun addEditorToolbarCommand(commandId: String, index: Int? = null) {
        val items = Settings.action_items.split("|").toMutableList()

        if (commandId in items) {
            if (index == null) return

            items.remove(commandId)
            items.add(index.coerceIn(0, items.size), commandId)
            Settings.action_items = items.joinToString("|")
            return
        }

        if (index != null) {
            items.add(index.coerceIn(0, items.size), commandId)
        } else {
            items.add(commandId)
        }

        Settings.action_items = items.joinToString("|")
    }

    @XedExtensionPoint
    fun addEditorToolbarCommand(command: Command, index: Int? = null) {
        addEditorToolbarCommand(command.id, index)
    }

    @XedExtensionPoint
    fun removeEditorToolbarCommand(commandId: String) {
        val items = Settings.action_items.split("|").toMutableList()

        if (!items.remove(commandId)) {
            return
        }

        Settings.action_items = items.joinToString("|")
    }

    @XedExtensionPoint
    fun removeEditorToolbarCommand(command: Command) {
        removeEditorToolbarCommand(command.id)
    }

    private val _globalCommands =
        MutableStateFlow<List<Command>>(
            listOf(
                CommandProvider.NewFileCommand,
                CommandProvider.SettingsCommand,
            )
        )

    val globalCommands: StateFlow<List<Command>> = _globalCommands.asStateFlow()

    @XedExtensionPoint
    fun addGlobalToolbarCommand(command: Command, index: Int? = null) {
        val existingIndex = _globalCommands.value.indexOf(command)

        if (existingIndex != -1) {
            _globalCommands.update { it - command }
        }

        val insertIndex =
            when {
                index != null -> index
                existingIndex != -1 -> existingIndex
                else -> _globalCommands.value.size
            }

        _globalCommands.update { list ->
            list.toMutableList().also { it.add(insertIndex.coerceIn(0, it.size), command) }
        }
    }

    @XedExtensionPoint
    fun addGlobalToolbarCommand(commandId: String, index: Int? = null) {
        val command = CommandProvider.getForId(commandId) ?: return
        addGlobalToolbarCommand(command, index)
    }

    @XedExtensionPoint
    fun removeGlobalToolbarCommand(command: Command) {
        _globalCommands.update { it - command }
    }

    @XedExtensionPoint
    fun removeGlobalToolbarCommand(commandId: String) {
        val command = CommandProvider.getForId(commandId) ?: return
        removeGlobalToolbarCommand(command)
    }
}
