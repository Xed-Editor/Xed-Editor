package com.rk.commands

import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.commands.editor.CopyCommand
import com.rk.commands.editor.CutCommand
import com.rk.commands.editor.DuplicateLineCommand
import com.rk.commands.editor.EmulateKeyCommand
import com.rk.commands.editor.JumpToLineCommand
import com.rk.commands.editor.LowerCaseCommand
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
import com.rk.commands.editor.UpperCaseCommand
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
    var commandList = listOf<Command>()

    lateinit var TerminalCommand: TerminalCommand
    lateinit var SettingsCommand: SettingsCommand
    lateinit var NewFileCommand: NewFileCommand
    lateinit var CommandPaletteCommand: CommandPaletteCommand
    lateinit var SearchFileFolderCommand: SearchFileFolderCommand
    lateinit var SearchCodeCommand: SearchCodeCommand
    lateinit var CutCommand: CutCommand
    lateinit var CopyCommand: CopyCommand
    lateinit var PasteCommand: PasteCommand
    lateinit var SelectAllCommand: SelectAllCommand
    lateinit var SelectWordCommand: SelectWordCommand
    lateinit var DuplicateLineCommand: DuplicateLineCommand
    lateinit var LowerCaseCommand: LowerCaseCommand
    lateinit var UpperCaseCommand: UpperCaseCommand
    lateinit var SaveCommand: SaveCommand
    lateinit var SaveAllCommand: SaveAllCommand
    lateinit var UndoCommand: UndoCommand
    lateinit var RedoCommand: RedoCommand
    lateinit var RunCommand: RunCommand
    lateinit var ToggleReadOnlyCommand: ToggleReadOnlyCommand
    lateinit var SearchCommand: SearchCommand
    lateinit var ReplaceCommand: ReplaceCommand
    lateinit var RefreshCommand: RefreshCommand
    lateinit var SyntaxHighlightingCommand: SyntaxHighlightingCommand
    lateinit var ToggleWordWrapCommand: ToggleWordWrapCommand
    lateinit var JumpToLineCommand: JumpToLineCommand
    lateinit var ShareCommand: ShareCommand
    lateinit var EmulateKeyCommand: EmulateKeyCommand
    lateinit var GoToDefinitionCommand: GoToDefinitionCommand
    lateinit var GoToReferencesCommand: GoToReferencesCommand
    lateinit var RenameSymbolCommand: RenameSymbolCommand
    lateinit var FormatDocumentCommand: FormatDocumentCommand
    lateinit var FormatSelectionCommand: FormatSelectionCommand

    fun buildCommands(mainViewModel: MainViewModel) {
        val mainActivity = MainActivity.instance!!
        val commandContext = CommandContext(mainActivity, mainViewModel)

        TerminalCommand = TerminalCommand(commandContext)
        SettingsCommand = SettingsCommand(commandContext)
        NewFileCommand = NewFileCommand(commandContext)
        CommandPaletteCommand = CommandPaletteCommand(commandContext)
        SearchFileFolderCommand = SearchFileFolderCommand(commandContext)
        SearchCodeCommand = SearchCodeCommand(commandContext)
        CutCommand = CutCommand(commandContext)
        CopyCommand = CopyCommand(commandContext)
        PasteCommand = PasteCommand(commandContext)
        SelectAllCommand = SelectAllCommand(commandContext)
        SelectWordCommand = SelectWordCommand(commandContext)
        DuplicateLineCommand = DuplicateLineCommand(commandContext)
        LowerCaseCommand = LowerCaseCommand(commandContext)
        UpperCaseCommand = UpperCaseCommand(commandContext)
        SaveCommand = SaveCommand(commandContext)
        SaveAllCommand = SaveAllCommand(commandContext)
        UndoCommand = UndoCommand(commandContext)
        RedoCommand = RedoCommand(commandContext)
        RunCommand = RunCommand(commandContext)
        ToggleReadOnlyCommand = ToggleReadOnlyCommand(commandContext)
        SearchCommand = SearchCommand(commandContext)
        ReplaceCommand = ReplaceCommand(commandContext)
        RefreshCommand = RefreshCommand(commandContext)
        SyntaxHighlightingCommand = SyntaxHighlightingCommand(commandContext)
        ToggleWordWrapCommand = ToggleWordWrapCommand(commandContext)
        JumpToLineCommand = JumpToLineCommand(commandContext)
        ShareCommand = ShareCommand(commandContext)
        EmulateKeyCommand = EmulateKeyCommand(commandContext)
        GoToDefinitionCommand = GoToDefinitionCommand(commandContext)
        GoToReferencesCommand = GoToReferencesCommand(commandContext)
        RenameSymbolCommand = RenameSymbolCommand(commandContext)
        FormatDocumentCommand = FormatDocumentCommand(commandContext)
        FormatSelectionCommand = FormatSelectionCommand(commandContext)

        commandList =
            listOf(
                TerminalCommand,
                SettingsCommand,
                NewFileCommand,
                CommandPaletteCommand,
                SearchFileFolderCommand,
                SearchCodeCommand,
                CutCommand,
                CopyCommand,
                PasteCommand,
                SelectAllCommand,
                SelectWordCommand,
                DuplicateLineCommand,
                LowerCaseCommand,
                UpperCaseCommand,
                SaveCommand,
                SaveAllCommand,
                UndoCommand,
                RedoCommand,
                RunCommand,
                ToggleReadOnlyCommand,
                SearchCommand,
                ReplaceCommand,
                RefreshCommand,
                SyntaxHighlightingCommand,
                ToggleWordWrapCommand,
                JumpToLineCommand,
                ShareCommand,
                EmulateKeyCommand,
                GoToDefinitionCommand,
                GoToReferencesCommand,
                RenameSymbolCommand,
                FormatDocumentCommand,
                FormatSelectionCommand,
                *getMutatorCommands(commandContext).toTypedArray(),
            )
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

    fun getForId(id: String): Command? = findRecursive(id, commandList)

    fun getParentCommand(command: Command): Command? = findParent(command, commandList)

    fun getForKeyCombination(keyCombination: KeyCombination): Command? {
        return commandList.find { it.defaultKeybinds == keyCombination }
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
