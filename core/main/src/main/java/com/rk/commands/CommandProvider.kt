package com.rk.commands

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.rk.DefaultScope
import com.rk.activities.main.MainViewModel
import com.rk.activities.settings.SettingsActivity
import com.rk.activities.terminal.Terminal
import com.rk.components.addDialog
import com.rk.icons.Edit_note
import com.rk.icons.XedIcons
import com.rk.lsp.goToDefinition
import com.rk.lsp.goToReferences
import com.rk.lsp.renameSymbol
import com.rk.mutation.Engine
import com.rk.mutation.Mutators
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.Runner
import com.rk.settings.app.InbuiltFeatures
import com.rk.tabs.EditorTab
import com.rk.utils.dialog
import com.rk.utils.showTerminalNotice
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.rk.mutation.MutatorAPI
import com.rk.utils.errorDialog

object CommandProvider {
    /** Get all registered commands */
    @Composable
    @OptIn(DelicateCoroutinesApi::class)
    fun getAll(viewModel: MainViewModel): List<Command> {
        val readModeText = stringResource(strings.read_mode)
        val editModeText = stringResource(strings.edit_mode)

        return buildList {
            // Core application commands
            add(
                Command(
                    id = "global.terminal",
                    label = mutableStateOf(stringResource(strings.terminal)),
                    action = { vm, act ->
                        showTerminalNotice(act!!) {
                            act.startActivity(Intent(act, Terminal::class.java))
                        }
                    },
                    isSupported = derivedStateOf { InbuiltFeatures.terminal.state.value },
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.terminal)),
                )
            )

            add(
                Command(
                    id = "global.settings",
                    label = mutableStateOf(stringResource(strings.settings)),
                    action = { vm, act ->
                        act!!.startActivity(Intent(act, SettingsActivity::class.java))
                    },
                    isSupported = mutableStateOf(true),
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(Icons.Outlined.Settings)
                )
            )

            add(
                Command(
                    id = "global.new_file",
                    label = mutableStateOf(stringResource(strings.new_file)),
                    action = { vm, _ ->
                        addDialog = true
                    },
                    isSupported = mutableStateOf(true),
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(Icons.Outlined.Add)
                )
            )

            add(
                Command(
                    id = "global.command_palette",
                    label = mutableStateOf(stringResource(strings.command_palette)),
                    action = { vm, _ ->
                        viewModel.showCommandPalette = true
                    },
                    isSupported = mutableStateOf(true),
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.command_palette))
                )
            )

            // Core editor commands
            add(
                Command(
                    id = "editor.cut",
                    label = mutableStateOf(stringResource(strings.cut)),
                    action = { vm, _ ->
                        val currentTab = vm.currentTab
                        if (currentTab is EditorTab) {
                            currentTab.editorState.editor.get()!!.cutText()
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = derivedStateOf {
                        val currentTab = viewModel.currentTab
                        currentTab is EditorTab && currentTab.editorState.editable
                    },
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.cut)),
                    keybinds = "Ctrl + X"
                )
            )

            add(
                Command(
                    id = "editor.copy",
                    label = mutableStateOf(stringResource(strings.copy)),
                    action = { vm, _ ->
                        val currentTab = vm.currentTab
                        if (currentTab is EditorTab) {
                            currentTab.editorState.editor.get()!!.copyText()
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.copy)),
                    keybinds = "Ctrl + C"
                )
            )

            add(
                Command(
                    id = "editor.paste",
                    label = mutableStateOf(stringResource(strings.paste)),
                    action = { vm, _ ->
                        val currentTab = vm.currentTab
                        if (currentTab is EditorTab) {
                            currentTab.editorState.editor.get()!!.pasteText()
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = derivedStateOf {
                        val currentTab = viewModel.currentTab
                        currentTab is EditorTab && currentTab.editorState.editable
                    },
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.paste)),
                    keybinds = "Ctrl + V"
                )
            )

            add(
                Command(
                    id = "editor.select_all",
                    label = mutableStateOf(stringResource(strings.select_all)),
                    action = { vm, _ ->
                        val currentTab = vm.currentTab
                        if (currentTab is EditorTab) {
                            currentTab.editorState.editor.get()!!.selectAll()
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.select_all)),
                    keybinds = "Ctrl + A"
                )
            )

            add(
                Command(
                    id = "editor.select_word",
                    label = mutableStateOf(stringResource(strings.select_word)),
                    action = { vm, _ ->
                        val currentTab = vm.currentTab
                        if (currentTab is EditorTab) {
                            currentTab.editorState.editor.get()!!.selectCurrentWord()
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.select)),
                    keybinds = "Ctrl + W"
                )
            )

            add(
                Command(
                    id = "editor.duplicate_line",
                    label = mutableStateOf(stringResource(strings.duplicate_line)),
                    action = { vm, _ ->
                        val currentTab = vm.currentTab
                        if (currentTab is EditorTab) {
                            currentTab.editorState.editor.get()!!.duplicateLine()
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = derivedStateOf {
                        val currentTab = viewModel.currentTab
                        currentTab is EditorTab && currentTab.editorState.editable
                    },
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.duplicate_line)),
                    keybinds = "Ctrl + D"
                )
            )

            add(
                Command(
                    id = "editor.save",
                    label = mutableStateOf(stringResource(strings.save)),
                    action = { vm, _ ->
                        vm.currentTab?.let {
                            if (it is EditorTab) {
                                GlobalScope.launch {
                                    it.save()
                                }
                            }
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = derivedStateOf {
                        val currentTab = viewModel.currentTab
                        currentTab is EditorTab && currentTab.file.canWrite()
                    },
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.save)),
                    keybinds = "Ctrl + S"
                )
            )

            add(
                Command(
                    id = "editor.save_all",
                    label = mutableStateOf(stringResource(strings.save_all)),
                    action = { vm, _ ->
                        vm.tabs.forEach {
                            if (it is EditorTab) {
                                GlobalScope.launch {
                                    it.save()
                                }
                            }
                        }
                    },
                    isSupported = derivedStateOf { viewModel.tabs.isNotEmpty() },
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.save))
                )
            )

            add(
                Command(
                    id = "editor.undo",
                    label = mutableStateOf(stringResource(strings.undo)),
                    action = { vm, _ ->
                        val currentTab = viewModel.currentTab
                        if (currentTab is EditorTab) {
                            currentTab.editorState.editor.get()?.apply {
                                if (canUndo()) {
                                    undo()
                                }
                            }
                            currentTab.editorState.updateUndoRedo()
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = derivedStateOf {
                        val currentTab = viewModel.currentTab
                        currentTab is EditorTab && currentTab.editorState.editable && currentTab.editorState.canUndo
                    },
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.undo)),
                    keybinds = "Ctrl + Z"
                )
            )

            add(
                Command(
                    id = "editor.redo",
                    label = mutableStateOf(stringResource(strings.redo)),
                    action = { vm, _ ->
                        val currentTab = viewModel.currentTab
                        if (currentTab is EditorTab) {
                            currentTab.editorState.editor.get()?.apply {
                                if (canRedo()) {
                                    redo()
                                }
                            }
                            currentTab.editorState.updateUndoRedo()
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = derivedStateOf {
                        val currentTab = viewModel.currentTab
                        currentTab is EditorTab && currentTab.editorState.editable && currentTab.editorState.canRedo
                    },
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.redo)),
                    keybinds = "Ctrl + Y"
                )
            )

            add(
                Command(
                    id = "editor.run",
                    label = mutableStateOf(stringResource(strings.run)),
                    action = { vm, act ->
                        DefaultScope.launch {
                            val currentTab = viewModel.currentTab
                            if (currentTab is EditorTab) {
                                Runner.run(
                                    context = act!!,
                                    fileObject = currentTab.file,
                                    onMultipleRunners = {
                                        currentTab.editorState.showRunnerDialog = true
                                        currentTab.editorState.runnersToShow = it
                                    }
                                )
                            }
                        }
                    },
                    isSupported = derivedStateOf {
                        val currentTab = viewModel.currentTab
                        currentTab is EditorTab && Runner.isRunnable(currentTab.file)
                    },
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.run))
                )
            )

            add(
                Command(
                    id = "editor.editable",
                    label = derivedStateOf {
                        val currentTab = viewModel.currentTab
                        if (currentTab is EditorTab && currentTab.editorState.editable) {
                            readModeText
                        } else {
                            editModeText
                        }
                    },
                    action = { vm, _ ->
                        val currentTab = viewModel.currentTab
                        if (currentTab is EditorTab) {
                            val editable = currentTab.editorState.editable
                            currentTab.editorState.editable = !editable
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = derivedStateOf {
                        val currentTab = viewModel.currentTab
                        currentTab is EditorTab && currentTab.file.canWrite()
                    },
                    icon = derivedStateOf {
                        val currentTab = viewModel.currentTab
                        if (currentTab is EditorTab && currentTab.editorState.editable) {
                            Icons.Outlined.Lock
                        } else {
                            Icons.Outlined.Edit
                        }
                    }
                )
            )

            add(
                Command(
                    id = "editor.search",
                    label = mutableStateOf(stringResource(strings.search)),
                    action = { vm, _ ->
                        val currentTab = viewModel.currentTab
                        if (currentTab is EditorTab) {
                            currentTab.editorState.isSearching = true
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.search)),
                    keybinds = "Ctrl + F"
                )
            )

            add(
                Command(
                    id = "editor.replace",
                    label = mutableStateOf(stringResource(strings.replace)),
                    action = { vm, _ ->
                        val currentTab = viewModel.currentTab
                        if (currentTab is EditorTab) {
                            currentTab.editorState.isSearching = true
                            currentTab.editorState.isReplaceShown = true
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.find_replace)),
                    keybinds = "Ctrl + H"
                )
            )

            add(
                Command(
                    id = "editor.refresh",
                    label = mutableStateOf(stringResource(strings.refresh)),
                    action = { vm, act ->
                        val currentTab = viewModel.currentTab
                        if (currentTab is EditorTab) {
                            if (currentTab.editorState.isDirty) {
                                dialog(
                                    context = act,
                                    title = strings.attention.getString(),
                                    msg = strings.ask_refresh.getString(),
                                    okString = strings.refresh,
                                    onCancel = {},
                                    onOk = { currentTab.refresh() }
                                )
                            } else {
                                currentTab.refresh()
                            }
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.refresh))
                )
            )

            add(
                Command(
                    id = "editor.syntax_highlighting",
                    label = mutableStateOf(stringResource(strings.highlighting)),
                    action = { vm, act ->
                        val currentTab = viewModel.currentTab
                        if (currentTab is EditorTab) {
                            currentTab.editorState.showSyntaxPanel = true
                        }
                    },
                    isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(XedIcons.Edit_note)
                )
            )

            add(
                Command(
                    id = "lsp.go_to_definition",
                    label = mutableStateOf(stringResource(strings.go_to_definition)),
                    action = { vm, act ->
                        val currentTab = viewModel.currentTab
                        if (currentTab is EditorTab) {
                            goToDefinition(DefaultScope, act!!, vm, currentTab)
                        }
                    },
                    isSupported = derivedStateOf { (viewModel.currentTab as? EditorTab)?.baseLspConnector?.isGoToDefinitionSupported() == true },
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.jump_to_element))
                )
            )

            add(
                Command(
                    id = "lsp.go_to_references",
                    label = mutableStateOf(stringResource(strings.go_to_references)),
                    action = { vm, act ->
                        val currentTab = viewModel.currentTab
                        if (currentTab is EditorTab) {
                            goToReferences(DefaultScope, act!!, vm, currentTab)
                        }
                    },
                    isSupported = derivedStateOf { (viewModel.currentTab as? EditorTab)?.baseLspConnector?.isGoToReferencesSupported() == true },
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.manage_search))
                )
            )

            add(
                Command(
                    id = "lsp.rename_symbol",
                    label = mutableStateOf(stringResource(strings.rename_symbol)),
                    action = { vm, act ->
                        val currentTab = viewModel.currentTab
                        if (currentTab is EditorTab) {
                            renameSymbol(DefaultScope, currentTab)
                        }
                    },
                    isSupported = derivedStateOf { (viewModel.currentTab as? EditorTab)?.baseLspConnector?.isGoToReferencesSupported() == true },
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.manage_search))
                )
            )

            // Mutators
            if (InbuiltFeatures.mutators.state.value) {
                addAll(
                    Mutators.mutators.map { mut ->
                        Command(
                            id = "mutators." + mut.name,
                            prefix = stringResource(strings.mutators),
                            label = mutableStateOf(mut.name),
                            action = { vm, _ ->
                                DefaultScope.launch {
                                    Engine(mut.script, DefaultScope)
                                        .start(
                                            onResult = { engine, result ->
                                                println(result)
                                            },
                                            onError = { t ->
                                                t.printStackTrace()
                                                errorDialog(t)
                                            },
                                            api = MutatorAPI::class.java
                                        )
                                }
                            },
                            isSupported = derivedStateOf { viewModel.currentTab is EditorTab },
                            isEnabled = mutableStateOf(true),
                            icon = mutableStateOf(ImageVector.vectorResource(drawables.run))
                        )
                    }
                )
            }
        }
    }

    /** Get a registered command by ID, returns null if not found */
    fun getForId(id: String?, commands: List<Command>): Command? = commands.find { it.id == id }
}