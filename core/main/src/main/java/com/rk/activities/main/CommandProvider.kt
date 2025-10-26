package com.rk.activities.main

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
import com.rk.activities.settings.SettingsActivity
import com.rk.activities.terminal.Terminal
import com.rk.components.addDialog
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

        // TODO: Add actions from `KEYBINDS.md`
        // TODO: Add LSP actions

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
                        val currentTab = vm.tabs[vm.currentTabIndex]
                        if (currentTab is EditorTab) {
                            addDialog = true
                        }
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
                        val currentTab = vm.tabs[vm.currentTabIndex]
                        if (currentTab is EditorTab) {
                            currentTab.editorState.showControlPanel = true
                        }
                    },
                    isSupported = mutableStateOf(true),
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.command_palette))
                )
            )

            // Core editor commands
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
                    isSupported = mutableStateOf(true),
                    isEnabled = derivedStateOf {
                        val currentTab = viewModel.tabs[viewModel.currentTabIndex]
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
                    isSupported = mutableStateOf(true),
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.save))
                )
            )

            add(
                Command(
                    id = "editor.undo",
                    label = mutableStateOf(stringResource(strings.undo)),
                    action = { vm, _ ->
                        val currentTab = vm.tabs[vm.currentTabIndex]
                        if (currentTab is EditorTab) {
                            currentTab.editorState.editor?.apply {
                                if (canUndo()) {
                                    undo()
                                }
                            }
                            currentTab.editorState.updateUndoRedo()
                        }
                    },
                    isSupported = mutableStateOf(true),
                    isEnabled = derivedStateOf {
                        val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                        currentTab is EditorTab && currentTab.editorState.canUndo
                    },
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.undo))
                )
            )

            add(
                Command(
                    id = "editor.redo",
                    label = mutableStateOf(stringResource(strings.redo)),
                    action = { vm, _ ->
                        val currentTab = vm.tabs[vm.currentTabIndex]
                        if (currentTab is EditorTab) {
                            currentTab.editorState.editor?.apply {
                                if (canRedo()) {
                                    redo()
                                }
                            }
                            currentTab.editorState.updateUndoRedo()
                        }
                    },
                    isSupported = mutableStateOf(true),
                    isEnabled = derivedStateOf {
                        val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                        currentTab is EditorTab && currentTab.editorState.canRedo
                    },
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.redo))
                )
            )

            add(
                Command(
                    id = "editor.run",
                    label = mutableStateOf(stringResource(strings.run)),
                    action = { vm, act ->
                        DefaultScope.launch {
                            val currentTab = vm.tabs[vm.currentTabIndex]
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
                        val currentTab = viewModel.tabs[viewModel.currentTabIndex]
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
                        val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                        if (currentTab is EditorTab && currentTab.editorState.editable) {
                            readModeText
                        } else {
                            editModeText
                        }
                    },
                    action = { vm, _ ->
                        val currentTab = vm.tabs[vm.currentTabIndex]
                        if (currentTab is EditorTab) {
                            val editable = currentTab.editorState.editable
                            currentTab.editorState.editable = !editable
                        }
                    },
                    isSupported = mutableStateOf(true),
                    isEnabled = derivedStateOf {
                        val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                        currentTab is EditorTab && currentTab.file.canWrite()
                    },
                    icon = derivedStateOf {
                        val currentTab = viewModel.tabs[viewModel.currentTabIndex]
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
                        val currentTab = vm.tabs[vm.currentTabIndex]
                        if (currentTab is EditorTab) {
                            currentTab.editorState.isSearching = true
                        }
                    },
                    isSupported = mutableStateOf(true),
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
                        val currentTab = vm.tabs[vm.currentTabIndex]
                        if (currentTab is EditorTab) {
                            currentTab.editorState.isSearching = true
                            currentTab.editorState.isReplaceShown = true
                        }
                    },
                    isSupported = mutableStateOf(true),
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
                        val currentTab = vm.tabs[vm.currentTabIndex]
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
                    isSupported = mutableStateOf(true),
                    isEnabled = mutableStateOf(true),
                    icon = mutableStateOf(ImageVector.vectorResource(drawables.refresh))
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
                            isSupported = mutableStateOf(true),
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