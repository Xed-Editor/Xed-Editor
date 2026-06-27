package com.rk.commands

import androidx.compose.runtime.mutableStateListOf
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
import com.rk.commands.editor.SortLinesAscendingCommand
import com.rk.commands.editor.SortLinesDescendingCommand
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
import com.rk.commands.lsp.FormatDocumentCommand
import com.rk.commands.lsp.FormatSelectionCommand
import com.rk.commands.lsp.GoToDefinitionCommand
import com.rk.commands.lsp.GoToReferencesCommand
import com.rk.commands.lsp.RenameSymbolCommand
import com.rk.extension.api.XedExtensionPoint

object CommandProvider {
    private val _commandList = mutableStateListOf<Command>()
    val commandList: List<Command>
        get() = _commandList

    lateinit var DocumentationCommand: DocumentationCommand
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
    lateinit var SortLinesAscendingCommand: SortLinesAscendingCommand
    lateinit var SortLinesDescendingCommand: SortLinesDescendingCommand
    lateinit var ShareCommand: ShareCommand
    lateinit var EmulateKeyCommand: EmulateKeyCommand
    lateinit var GoToDefinitionCommand: GoToDefinitionCommand
    lateinit var GoToReferencesCommand: GoToReferencesCommand
    lateinit var RenameSymbolCommand: RenameSymbolCommand
    lateinit var FormatDocumentCommand: FormatDocumentCommand
    lateinit var FormatSelectionCommand: FormatSelectionCommand

    fun buildCommands() =
        synchronized(this) {
            registerBuiltin(DocumentationCommand()) { DocumentationCommand = it }
            registerBuiltin(SettingsCommand()) { SettingsCommand = it }
            registerBuiltin(NewFileCommand()) { NewFileCommand = it }
            registerBuiltin(CommandPaletteCommand()) { CommandPaletteCommand = it }
            registerBuiltin(SearchFileFolderCommand()) { SearchFileFolderCommand = it }
            registerBuiltin(SearchCodeCommand()) { SearchCodeCommand = it }
            registerBuiltin(CutCommand()) { CutCommand = it }
            registerBuiltin(CopyCommand()) { CopyCommand = it }
            registerBuiltin(PasteCommand()) { PasteCommand = it }
            registerBuiltin(SelectAllCommand()) { SelectAllCommand = it }
            registerBuiltin(SelectWordCommand()) { SelectWordCommand = it }
            registerBuiltin(DuplicateLineCommand()) { DuplicateLineCommand = it }
            registerBuiltin(LowerCaseCommand()) { LowerCaseCommand = it }
            registerBuiltin(UpperCaseCommand()) { UpperCaseCommand = it }
            registerBuiltin(SaveCommand()) { SaveCommand = it }
            registerBuiltin(SaveAllCommand()) { SaveAllCommand = it }
            registerBuiltin(UndoCommand()) { UndoCommand = it }
            registerBuiltin(RedoCommand()) { RedoCommand = it }
            registerBuiltin(RunCommand()) { RunCommand = it }
            registerBuiltin(ToggleReadOnlyCommand()) { ToggleReadOnlyCommand = it }
            registerBuiltin(SearchCommand()) { SearchCommand = it }
            registerBuiltin(ReplaceCommand()) { ReplaceCommand = it }
            registerBuiltin(RefreshCommand()) { RefreshCommand = it }
            registerBuiltin(SyntaxHighlightingCommand()) { SyntaxHighlightingCommand = it }
            registerBuiltin(ToggleWordWrapCommand()) { ToggleWordWrapCommand = it }
            registerBuiltin(JumpToLineCommand()) { JumpToLineCommand = it }
            registerBuiltin(SortLinesAscendingCommand()) { SortLinesAscendingCommand = it }
            registerBuiltin(SortLinesDescendingCommand()) { SortLinesDescendingCommand = it }
            registerBuiltin(ShareCommand()) { ShareCommand = it }
            registerBuiltin(EmulateKeyCommand()) { EmulateKeyCommand = it }
            registerBuiltin(GoToDefinitionCommand()) { GoToDefinitionCommand = it }
            registerBuiltin(GoToReferencesCommand()) { GoToReferencesCommand = it }
            registerBuiltin(RenameSymbolCommand()) { RenameSymbolCommand = it }
            registerBuiltin(FormatDocumentCommand()) { FormatDocumentCommand = it }
            registerBuiltin(FormatSelectionCommand()) { FormatSelectionCommand = it }
        }

    private fun <T : Command> registerBuiltin(command: T, assign: (T) -> Unit) {
        if (_commandList.contains(command)) return
        assign(command)
        _commandList.add(command)
    }

    @XedExtensionPoint
    fun registerCommand(command: Command) {
        val index = _commandList.indexOf(command)
        if (index >= 0) {
            _commandList[index] = command
        } else {
            _commandList.add(command)
        }
    }

    @XedExtensionPoint
    fun unregisterCommand(command: Command) {
        _commandList.remove(command)
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
