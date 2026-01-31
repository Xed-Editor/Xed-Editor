package com.rk.commands

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
import com.rk.commands.global.DocumentationCommand
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

object CommandProvider {
    private val mutableCommandList = mutableListOf<Command>()
    val commandList: List<Command>
        get() = mutableCommandList.toList()

    lateinit var DocumentationCommand: DocumentationCommand
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
        val commandContext = CommandContext(mainViewModel)

        registerBuiltin(DocumentationCommand(commandContext)) { DocumentationCommand = it }
        registerBuiltin(TerminalCommand(commandContext)) { TerminalCommand = it }
        registerBuiltin(SettingsCommand(commandContext)) { SettingsCommand = it }
        registerBuiltin(NewFileCommand(commandContext)) { NewFileCommand = it }
        registerBuiltin(CommandPaletteCommand(commandContext)) { CommandPaletteCommand = it }
        registerBuiltin(SearchFileFolderCommand(commandContext)) { SearchFileFolderCommand = it }
        registerBuiltin(SearchCodeCommand(commandContext)) { SearchCodeCommand = it }
        registerBuiltin(CutCommand(commandContext)) { CutCommand = it }
        registerBuiltin(CopyCommand(commandContext)) { CopyCommand = it }
        registerBuiltin(PasteCommand(commandContext)) { PasteCommand = it }
        registerBuiltin(SelectAllCommand(commandContext)) { SelectAllCommand = it }
        registerBuiltin(SelectWordCommand(commandContext)) { SelectWordCommand = it }
        registerBuiltin(DuplicateLineCommand(commandContext)) { DuplicateLineCommand = it }
        registerBuiltin(LowerCaseCommand(commandContext)) { LowerCaseCommand = it }
        registerBuiltin(UpperCaseCommand(commandContext)) { UpperCaseCommand = it }
        registerBuiltin(SaveCommand(commandContext)) { SaveCommand = it }
        registerBuiltin(SaveAllCommand(commandContext)) { SaveAllCommand = it }
        registerBuiltin(UndoCommand(commandContext)) { UndoCommand = it }
        registerBuiltin(RedoCommand(commandContext)) { RedoCommand = it }
        registerBuiltin(RunCommand(commandContext)) { RunCommand = it }
        registerBuiltin(ToggleReadOnlyCommand(commandContext)) { ToggleReadOnlyCommand = it }
        registerBuiltin(SearchCommand(commandContext)) { SearchCommand = it }
        registerBuiltin(ReplaceCommand(commandContext)) { ReplaceCommand = it }
        registerBuiltin(RefreshCommand(commandContext)) { RefreshCommand = it }
        registerBuiltin(SyntaxHighlightingCommand(commandContext)) { SyntaxHighlightingCommand = it }
        registerBuiltin(ToggleWordWrapCommand(commandContext)) { ToggleWordWrapCommand = it }
        registerBuiltin(JumpToLineCommand(commandContext)) { JumpToLineCommand = it }
        registerBuiltin(ShareCommand(commandContext)) { ShareCommand = it }
        registerBuiltin(EmulateKeyCommand(commandContext)) { EmulateKeyCommand = it }
        registerBuiltin(GoToDefinitionCommand(commandContext)) { GoToDefinitionCommand = it }
        registerBuiltin(GoToReferencesCommand(commandContext)) { GoToReferencesCommand = it }
        registerBuiltin(RenameSymbolCommand(commandContext)) { RenameSymbolCommand = it }
        registerBuiltin(FormatDocumentCommand(commandContext)) { FormatDocumentCommand = it }
        registerBuiltin(FormatSelectionCommand(commandContext)) { FormatSelectionCommand = it }
    }

    private fun <T : Command> registerBuiltin(command: T, assign: (T) -> Unit) {
        assign(command)
        mutableCommandList.add(command)
    }

    fun registerCommand(command: Command) {
        if (!mutableCommandList.contains(command)) {
            mutableCommandList.add(command)
        }
    }

    fun unregisterCommand(command: Command) {
        mutableCommandList.remove(command)
    }

    fun getForId(id: String): Command? = findRecursive(id, commandList)

    fun getParentCommand(command: Command): Command? = findParent(command, commandList)

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
