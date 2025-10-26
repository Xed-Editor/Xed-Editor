package com.rk.xededitor.ui.activities.main

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
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
    @OptIn(DelicateCoroutinesApi::class)
    @Composable
    fun getAll(): List<Command> = buildList {
        // Core application commands
        add(
            Command(
                id = "global.terminal",
                label = stringResource(strings.terminal),
                action = { viewModel, activity ->
                    showTerminalNotice(activity!!) {
                        activity.startActivity(Intent(activity, Terminal::class.java))
                    }
                },
                isSupported = { _, _ -> InbuiltFeatures.terminal.state.value },
                icon = ImageVector.vectorResource(drawables.terminal)
            )
        )

        add(
            Command(
                id = "global.settings",
                label = stringResource(strings.settings),
                action = { viewModel, activity ->
                    activity!!.startActivity(Intent(activity, SettingsActivity::class.java))
                },
                icon = Icons.Outlined.Settings
            )
        )

        add(
            Command(
                id = "global.new_file",
                label = stringResource(strings.new_file),
                action = { viewModel, _ ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    if (currentTab is EditorTab) {
                        addDialog = true
                    }
                },
                icon = Icons.Outlined.Add
            )
        )

        add(
            Command(
                id = "global.command_palette",
                label = stringResource(strings.command_palette),
                action = { viewModel, _ ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    if (currentTab is EditorTab) {
                        currentTab.editorState.showControlPanel = true
                    }
                },
                // TODO: Add icon
            )
        )

        // Core editor commands
        add(
            Command(
                id = "editor.save",
                label = stringResource(strings.save),
                action = { viewModel, _ ->
                    viewModel.currentTab?.let {
                        if (it is EditorTab) {
                            GlobalScope.launch {
                                it.save()
                            }
                        }
                    }
                },
                isEnabled = { viewModel, _ ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    return@Command currentTab is EditorTab && currentTab.file.canWrite()
                },
                icon = ImageVector.vectorResource(drawables.save)
            )
        )

        add(
            Command(
                id = "editor.save_all",
                label = stringResource(strings.save_all),
                action = { viewModel, _ ->
                    viewModel.tabs.forEach {
                        if (it is EditorTab) {
                            GlobalScope.launch {
                                it.save()
                            }
                        }
                    }
                },
                icon = ImageVector.vectorResource(drawables.save)
            )
        )

        add(
            Command(
                id = "editor.undo",
                label = stringResource(strings.undo),
                action = { viewModel, _ ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    if (currentTab is EditorTab) {
                        currentTab.editorState.editor?.apply {
                            if (canUndo()) {
                                undo()
                            }
                        }
                        currentTab.editorState.updateUndoRedo()
                    }
                },
                isEnabled = { viewModel, _ ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    return@Command currentTab is EditorTab && currentTab.editorState.canUndo
                },
                icon = ImageVector.vectorResource(drawables.undo)
            )
        )

        add(
            Command(
                id = "editor.redo",
                label = stringResource(strings.redo),
                action = { viewModel, _ ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    if (currentTab is EditorTab) {
                        currentTab.editorState.editor?.apply {
                            if (canRedo()) {
                                redo()
                            }
                        }
                        currentTab.editorState.updateUndoRedo()
                    }
                },
                isEnabled = { viewModel, _ ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    return@Command currentTab is EditorTab && currentTab.editorState.canRedo
                },
                icon = ImageVector.vectorResource(drawables.redo)
            )
        )

        add(
            Command(
                id = "editor.run",
                label = stringResource(strings.run),
                action = { viewModel, activity ->
                    DefaultScope.launch {
                        val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                        if (currentTab is EditorTab) {
                            Runner.run(
                                context = activity!!,
                                fileObject = currentTab.file,
                                onMultipleRunners = {
                                    currentTab.editorState.showRunnerDialog = true
                                    currentTab.editorState.runnersToShow = it
                                }
                            )
                        }
                    }
                },
                isSupported = { viewModel, _ ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    return@Command currentTab is EditorTab && Runner.isRunnable(currentTab.file)
                },
                icon = ImageVector.vectorResource(drawables.run)
            )
        )

        add(
            Command(
                id = "editor.editable",
                labelProvider = { viewModel, _ ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    if (currentTab is EditorTab && currentTab.editorState.editable) {
                        return@Command stringResource(strings.read_mode)
                    }
                    return@Command stringResource(strings.edit_mode)
                },
                action = { viewModel, _ ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    if (currentTab is EditorTab) {
                        val editable = currentTab.editorState.editor?.editable
                        editable?.let { currentTab.editorState.editor?.editable = !it }
                    }
                },
                isEnabled = { viewModel, _ ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    return@Command currentTab is EditorTab && currentTab.file.canWrite()
                },
                iconProvider = { viewModel, _ ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    if (currentTab is EditorTab && currentTab.editorState.editable) {
                        return@Command Icons.Outlined.Lock
                    }
                    return@Command Icons.Outlined.Edit
                }
            )
        )

        add(
            Command(
                id = "editor.search",
                label = stringResource(strings.search),
                action = { viewModel, _ ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    if (currentTab is EditorTab) {
                        currentTab.editorState.isSearching = true
                    }
                },
                icon = ImageVector.vectorResource(drawables.search)
            )
        )

        add(
            Command(
                id = "editor.refresh",
                label = stringResource(strings.refresh),
                action = { viewModel, activity ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    if (currentTab is EditorTab) {
                        if (currentTab.editorState.isDirty) {
                            dialog(
                                context = activity,
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
                icon = ImageVector.vectorResource(drawables.refresh)
            )
        )

        // Mutators
        if (InbuiltFeatures.mutators.state.value) {
            addAll(
                Mutators.mutators.map { mut ->
                    Command(
                        id = "mutators." + mut.name,
                        prefix = stringResource(strings.mutators),
                        label = mut.name,
                        action = { viewModel, _ ->
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
                        icon = ImageVector.vectorResource(drawables.run)
                    )
                }
            )
        }
    }

    /** Get a registered command by ID, returns null if not found */
    fun getForId(id: String?, commands: List<Command>): Command? = commands.find { it.id == id }
}