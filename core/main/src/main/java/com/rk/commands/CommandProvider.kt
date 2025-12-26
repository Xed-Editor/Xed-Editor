package com.rk.commands

import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.commands.editor.CopyCommand
import com.rk.commands.editor.CutCommand
import com.rk.commands.editor.DuplicateLineCommand
import com.rk.commands.editor.EmulateKeyCommand
import com.rk.commands.editor.JumpToLineCommand
import com.rk.commands.editor.PasteCommand
import com.rk.commands.editor.RedoCommand
import com.rk.commands.editor.RefreshCommand
import com.rk.commands.editor.ReplaceCommand
import com.rk.commands.editor.RunCommand
import com.rk.commands.editor.SaveCommand
import com.rk.commands.editor.SearchCommand
import com.rk.commands.editor.SelectAllCommand
import com.rk.commands.editor.SelectWordCommand
import com.rk.commands.editor.ShareCommand
import com.rk.commands.editor.SyntaxHighlightingCommand
import com.rk.commands.editor.ToggleReadOnlyCommand
import com.rk.commands.editor.ToggleWordWrapCommand
import com.rk.commands.editor.UndoCommand
import com.rk.commands.global.CommandPaletteCommand
import com.rk.commands.global.NewFileCommand
import com.rk.commands.global.SaveAllCommand
import com.rk.commands.global.SearchCodeCommand
import com.rk.commands.global.SearchFileFolderCommand
import com.rk.commands.global.SettingsCommand
import com.rk.commands.global.TerminalCommand
import com.rk.commands.lsp.FormatDocumentCommand
import com.rk.commands.lsp.FormatSelectionCommand
import com.rk.commands.lsp.GoToDefinitionCommand
import com.rk.commands.lsp.GoToReferencesCommand
import com.rk.commands.lsp.RenameSymbolCommand
import com.rk.icons.Icon
import com.rk.mutation.Engine
import com.rk.mutation.MutatorAPI
import com.rk.mutation.Mutators
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.app.InbuiltFeatures
import com.rk.utils.errorDialog
import kotlinx.coroutines.launch

object CommandProvider {
    var globalCommands = listOf<Command>()

    fun buildCommands(mainViewModel: MainViewModel): List<Command> {
        val commandContext = CommandContext(MainActivity.instance!!, mainViewModel)

        return listOf(
                TerminalCommand(commandContext),
                SettingsCommand(commandContext),
                NewFileCommand(commandContext),
                CommandPaletteCommand(commandContext),
                SearchFileFolderCommand(commandContext),
                SearchCodeCommand(commandContext),
                CutCommand(commandContext),
                CopyCommand(commandContext),
                PasteCommand(commandContext),
                SelectAllCommand(commandContext),
                SelectWordCommand(commandContext),
                DuplicateLineCommand(commandContext),
                SaveCommand(commandContext),
                SaveAllCommand(commandContext),
                UndoCommand(commandContext),
                RedoCommand(commandContext),
                RunCommand(commandContext),
                ToggleReadOnlyCommand(commandContext),
                SearchCommand(commandContext),
                ReplaceCommand(commandContext),
                RefreshCommand(commandContext),
                SyntaxHighlightingCommand(commandContext),
                ToggleWordWrapCommand(commandContext),
                JumpToLineCommand(commandContext),
                ShareCommand(commandContext),
                EmulateKeyCommand(commandContext),
                GoToDefinitionCommand(commandContext),
                GoToReferencesCommand(commandContext),
                RenameSymbolCommand(commandContext),
                FormatDocumentCommand(commandContext),
                FormatSelectionCommand(commandContext),
                *getMutatorCommands(commandContext).toTypedArray(),
            )
            .also { globalCommands = it }
    }

    fun getMutatorCommands(commandContext: CommandContext): List<Command> {
        if (!InbuiltFeatures.mutators.state.value) return emptyList()

        return Mutators.mutators.map { mut ->
            object : EditorCommand(commandContext) {
                override val id: String = "mutators.${mut.name}"

                override val prefix: String = strings.mutators.getString()

                override fun getLabel(): String = mut.name

                override fun action(editorActionContext: EditorActionContext) {
                    DefaultScope.launch {
                        Engine(mut.script, DefaultScope)
                            .start(
                                onResult = { _, result -> println(result) },
                                onError = { t ->
                                    t.printStackTrace()
                                    errorDialog(t)
                                },
                                api = MutatorAPI::class.java,
                            )
                    }
                }

                override fun getIcon(): Icon = Icon.DrawableRes(drawables.run)
            }
        }
    }

    fun getForId(id: String, commands: List<Command>): Command? = findRecursive(id, commands)

    fun getForId(id: String): Command? = findRecursive(id, globalCommands)

    fun getParentCommand(command: Command): Command? = findParent(command, globalCommands)

    fun getForKeyCombination(keyCombination: KeyCombination): Command? {
        return globalCommands.find { it.defaultKeybinds == keyCombination }
    }

    private fun findParent(target: Command, commands: List<Command>): Command? {
        for (parent in commands) {
            val children = parent.childCommands
            if (children.any { it.id == target.id }) return parent

            val match = findParent(target, children)
            if (match != null) return match
        }
        return null
    }

    private fun findRecursive(id: String, commands: List<Command>): Command? {
        for (command in commands) {
            if (command.id == id) return command
            val children = command.childCommands
            val match = findRecursive(id, children)
            if (match != null) return match
        }
        return null
    }
}
