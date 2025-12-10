package com.rk.commands

import android.content.Intent
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.rk.DefaultScope
import com.rk.activities.main.MainViewModel
import com.rk.activities.settings.SettingsActivity
import com.rk.activities.terminal.Terminal
import com.rk.components.addDialog
import com.rk.file.FileType
import com.rk.filetree.projects
import com.rk.lsp.formatDocument
import com.rk.lsp.formatDocumentRange
import com.rk.lsp.goToDefinition
import com.rk.lsp.goToReferences
import com.rk.lsp.renameSymbol
import com.rk.mutation.Engine
import com.rk.mutation.MutatorAPI
import com.rk.mutation.Mutators
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.Runner
import com.rk.settings.app.InbuiltFeatures
import com.rk.tabs.editor.EditorTab
import com.rk.utils.dialog
import com.rk.utils.errorDialog
import com.rk.utils.showTerminalNotice
import java.util.Locale.getDefault
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object CommandProvider {
    var globalCommands = listOf<Command>()

    fun buildCommands(viewModel: MainViewModel): List<Command> {
        return buildList {
                addAll(getAppCommands(viewModel))
                addAll(getEditorCommands(viewModel))
                addAll(getLspCommands(viewModel))
                addAll(getMutatorCommands(viewModel))
            }
            .also { globalCommands = it }
    }

    private fun getAppCommands(viewModel: MainViewModel): List<Command> {
        return listOf(
            Command(
                id = "global.terminal",
                label = mutableStateOf(strings.terminal.getString()),
                action = { _, act ->
                    showTerminalNotice(act!!) {
                        val intent =
                            Intent(act, Terminal::class.java).apply {
                                val currentFile = viewModel.currentTab?.file ?: return@apply
                                val currentPath = currentFile.getAbsolutePath()
                                val project =
                                    projects
                                        .filter { currentPath.startsWith(it.fileObject.getAbsolutePath()) }
                                        .maxByOrNull { it.fileObject.getAbsolutePath().length } ?: return@apply
                                putExtra("cwd", project.fileObject.getAbsolutePath())
                            }
                        act.startActivity(intent)
                    }
                },
                isSupported = derivedStateOf { InbuiltFeatures.terminal.state.value },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.terminal),
            ),
            Command(
                id = "global.settings",
                label = mutableStateOf(strings.settings.getString()),
                action = { _, act -> act!!.startActivity(Intent(act, SettingsActivity::class.java)) },
                isSupported = mutableStateOf(true),
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.settings),
            ),
            Command(
                id = "global.new_file",
                label = mutableStateOf(strings.new_file.getString()),
                action = { _, _ -> addDialog = true },
                isSupported = mutableStateOf(true),
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.add),
            ),
            Command(
                id = "global.command_palette",
                label = mutableStateOf(strings.command_palette.getString()),
                action = { _, _ -> viewModel.showCommandPalette = true },
                isSupported = mutableStateOf(true),
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.command_palette),
            ),
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun getEditorCommands(viewModel: MainViewModel): List<Command> {
        return listOf(
            Command(
                id = "editor.cut",
                label = mutableStateOf(strings.cut.getString()),
                action = { vm, _ -> (vm.currentTab as? EditorTab)?.editorState?.editor?.get()?.cutText() },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled = derivedStateOf { (viewModel.currentTab as? EditorTab)?.editorState?.editable == true },
                icon = mutableIntStateOf(drawables.cut),
                keybinds = "Ctrl + X",
            ),
            Command(
                id = "editor.copy",
                label = mutableStateOf(strings.copy.getString()),
                action = { vm, _ -> (vm.currentTab as? EditorTab)?.editorState?.editor?.get()?.copyText() },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.copy),
                keybinds = "Ctrl + C",
            ),
            Command(
                id = "editor.paste",
                label = mutableStateOf(strings.paste.getString()),
                action = { vm, _ -> (vm.currentTab as? EditorTab)?.editorState?.editor?.get()?.pasteText() },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled = derivedStateOf { (viewModel.currentTab as? EditorTab)?.editorState?.editable == true },
                icon = mutableIntStateOf(drawables.paste),
                keybinds = "Ctrl + V",
            ),
            Command(
                id = "editor.select_all",
                label = mutableStateOf(strings.select_all.getString()),
                action = { vm, _ -> (vm.currentTab as? EditorTab)?.editorState?.editor?.get()?.selectAll() },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.select_all),
                keybinds = "Ctrl + A",
            ),
            Command(
                id = "editor.select_word",
                label = mutableStateOf(strings.select_word.getString()),
                action = { vm, _ -> (vm.currentTab as? EditorTab)?.editorState?.editor?.get()?.selectCurrentWord() },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.select),
                keybinds = "Ctrl + W",
            ),
            Command(
                id = "editor.duplicate_line",
                label = mutableStateOf(strings.duplicate_line.getString()),
                action = { vm, _ -> (vm.currentTab as? EditorTab)?.editorState?.editor?.get()?.duplicateLine() },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled = derivedStateOf { (viewModel.currentTab as? EditorTab)?.editorState?.editable == true },
                icon = mutableIntStateOf(drawables.duplicate_line),
                keybinds = "Ctrl + D",
            ),
            Command(
                id = "editor.save",
                label = mutableStateOf(strings.save.getString()),
                action = { vm, _ ->
                    val currentTab = vm.currentTab as? EditorTab ?: return@Command
                    GlobalScope.launch(Dispatchers.IO) { currentTab.save() }
                },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled = derivedStateOf { (viewModel.currentTab as? EditorTab)?.file?.canWrite() == true },
                icon = mutableIntStateOf(drawables.save),
                keybinds = "Ctrl + S",
            ),
            Command(
                id = "editor.save_all",
                label = mutableStateOf(strings.save_all.getString()),
                action = { vm, _ ->
                    vm.tabs.filterIsInstance<EditorTab>().forEach { GlobalScope.launch(Dispatchers.IO) { it.save() } }
                },
                isSupported = derivedStateOf { viewModel.tabs.isNotEmpty() },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.save),
            ),
            Command(
                id = "editor.undo",
                label = mutableStateOf(strings.undo.getString()),
                action = { vm, _ ->
                    val currentTab = vm.currentTab as? EditorTab ?: return@Command
                    currentTab.editorState.editor.get()?.apply { if (canUndo()) undo() }
                    currentTab.editorState.updateUndoRedo()
                },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled =
                    derivedStateOf {
                        val tab = viewModel.currentTab as? EditorTab
                        tab != null && tab.editorState.editable && tab.editorState.canUndo
                    },
                icon = mutableIntStateOf(drawables.undo),
                keybinds = "Ctrl + Z",
            ),
            Command(
                id = "editor.redo",
                label = mutableStateOf(strings.redo.getString()),
                action = { vm, _ ->
                    val currentTab = vm.currentTab as? EditorTab ?: return@Command
                    currentTab.editorState.editor.get()?.apply { if (canRedo()) redo() }
                    currentTab.editorState.updateUndoRedo()
                },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled =
                    derivedStateOf {
                        val tab = viewModel.currentTab as? EditorTab
                        tab != null && tab.editorState.editable && tab.editorState.canRedo
                    },
                icon = mutableIntStateOf(drawables.redo),
                keybinds = "Ctrl + Y",
            ),
            Command(
                id = "editor.run",
                label = mutableStateOf(strings.run.getString()),
                action = { vm, act ->
                    DefaultScope.launch {
                        val currentTab = vm.currentTab as? EditorTab ?: return@launch
                        Runner.run(
                            context = act!!,
                            fileObject = currentTab.file,
                            onMultipleRunners = {
                                currentTab.editorState.showRunnerDialog = true
                                currentTab.editorState.runnersToShow = it
                            },
                        )
                    }
                },
                isSupported =
                    derivedStateOf {
                        val currentTab = viewModel.currentTab
                        currentTab is EditorTab && Runner.isRunnable(currentTab.file)
                    },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.run),
            ),
            Command(
                id = "editor.editable",
                label =
                    derivedStateOf {
                        if ((viewModel.currentTab as? EditorTab)?.editorState?.editable == true) {
                            strings.read_mode.getString()
                        } else {
                            strings.edit_mode.getString()
                        }
                    },
                action = { vm, _ ->
                    val currentTab = vm.currentTab as? EditorTab
                    currentTab?.editorState?.let { it.editable = !it.editable }
                },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled = derivedStateOf { (viewModel.currentTab as? EditorTab)?.file?.canWrite() == true },
                icon =
                    derivedStateOf {
                        if ((viewModel.currentTab as? EditorTab)?.editorState?.editable == true) drawables.lock
                        else drawables.edit
                    },
            ),
            Command(
                id = "editor.search",
                label = mutableStateOf(strings.search.getString()),
                action = { vm, _ -> (vm.currentTab as? EditorTab)?.editorState?.isSearching = true },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.search),
                keybinds = "Ctrl + F",
            ),
            Command(
                id = "editor.replace",
                label = mutableStateOf(strings.replace.getString()),
                action = { vm, _ ->
                    val currentTab = vm.currentTab as? EditorTab
                    currentTab?.editorState?.apply {
                        isSearching = true
                        isReplaceShown = true
                    }
                },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.find_replace),
                keybinds = "Ctrl + H",
            ),
            Command(
                id = "editor.refresh",
                label = mutableStateOf(strings.refresh.getString()),
                action = { vm, act ->
                    val currentTab = vm.currentTab as? EditorTab ?: return@Command
                    if (currentTab.editorState.isDirty) {
                        dialog(
                            context = act,
                            title = strings.attention.getString(),
                            msg = strings.ask_refresh.getString(),
                            okString = strings.refresh,
                            onCancel = {},
                            onOk = { currentTab.refresh() },
                        )
                    } else {
                        currentTab.refresh()
                    }
                },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.refresh),
            ),
            Command(
                id = "editor.syntax_highlighting",
                label = mutableStateOf(strings.highlighting.getString()),
                action = { _, _ -> viewModel.showCommandPalette = true },
                childCommands =
                    FileType.entries
                        .filter { it.textmateScope != null }
                        .map { fileType ->
                            Command(
                                id = "editor.syntax_highlighting.${fileType.name.lowercase(getDefault())}",
                                label = mutableStateOf(fileType.title),
                                action = { vm, _ ->
                                    (vm.currentTab as? EditorTab)?.editorState?.textmateScope = fileType.textmateScope!!
                                },
                                isSupported = mutableStateOf(true),
                                isEnabled = mutableStateOf(true),
                                icon = mutableIntStateOf(fileType.icon ?: drawables.file),
                            )
                        },
                childSearchPlaceholder = strings.select_language.getString(),
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.edit_note),
            ),
        )
    }

    private fun getLspCommands(viewModel: MainViewModel): List<Command> {
        return listOf(
            Command(
                id = "lsp.go_to_definition",
                label = mutableStateOf(strings.go_to_definition.getString()),
                action = { vm, act ->
                    (vm.currentTab as? EditorTab)?.let { goToDefinition(DefaultScope, act!!, vm, it) }
                },
                isSupported =
                    derivedStateOf {
                        (viewModel.currentTab as? EditorTab)?.baseLspConnector?.isGoToDefinitionSupported() == true
                    },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.jump_to_element),
            ),
            Command(
                id = "lsp.go_to_references",
                label = mutableStateOf(strings.go_to_references.getString()),
                action = { vm, act ->
                    (vm.currentTab as? EditorTab)?.let { goToReferences(DefaultScope, act!!, vm, it) }
                },
                isSupported =
                    derivedStateOf {
                        (viewModel.currentTab as? EditorTab)?.baseLspConnector?.isGoToReferencesSupported() == true
                    },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.manage_search),
            ),
            Command(
                id = "lsp.rename_symbol",
                label = mutableStateOf(strings.rename_symbol.getString()),
                action = { vm, _ -> (vm.currentTab as? EditorTab)?.let { renameSymbol(DefaultScope, it) } },
                isSupported =
                    derivedStateOf {
                        (viewModel.currentTab as? EditorTab)?.baseLspConnector?.isGoToReferencesSupported() == true
                    },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.manage_search),
            ),
            Command(
                id = "lsp.format_document",
                label = mutableStateOf(strings.format_document.getString()),
                action = { vm, _ -> (vm.currentTab as? EditorTab)?.let { formatDocument(DefaultScope, it) } },
                isSupported =
                    derivedStateOf {
                        (viewModel.currentTab as? EditorTab)?.baseLspConnector?.isFormattingSupported() == true
                    },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.auto_fix),
            ),
            Command(
                id = "lsp.format_selection",
                label = mutableStateOf(strings.format_selection.getString()),
                action = { vm, _ -> (vm.currentTab as? EditorTab)?.let { formatDocumentRange(DefaultScope, it) } },
                isSupported =
                    derivedStateOf {
                        (viewModel.currentTab as? EditorTab)?.baseLspConnector?.isRangeFormattingSupported() == true
                    },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.auto_fix),
            ),
        )
    }

    private fun getMutatorCommands(viewModel: MainViewModel): List<Command> {
        if (!InbuiltFeatures.mutators.state.value) return emptyList()

        return Mutators.mutators.map { mut ->
            Command(
                id = "mutators.${mut.name}",
                prefix = strings.mutators.getString(),
                label = mutableStateOf(mut.name),
                action = { _, _ ->
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
                },
                isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                isEnabled = mutableStateOf(true),
                icon = mutableIntStateOf(drawables.run),
            )
        }
    }

    fun getForId(id: String, commands: List<Command>): Command? = findRecursive(id, commands)

    fun getForId(id: String): Command? = findRecursive(id, globalCommands)

    fun getParentCommand(command: Command): Command? = findParent(command, globalCommands)

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
