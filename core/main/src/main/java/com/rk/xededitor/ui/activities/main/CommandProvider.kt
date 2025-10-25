package com.rk.xededitor.ui.activities.main

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rk.DefaultScope
import com.rk.libcommons.errorDialog
import com.rk.libcommons.showTerminalNotice
import com.rk.mutator_engine.Engine
import com.rk.resources.strings
import com.rk.tabs.EditorTab
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import com.rk.xededitor.ui.activities.terminal.Terminal
import com.rk.xededitor.ui.components.updateUndoRedo
import com.rk.xededitor.ui.screens.settings.app.InbuiltFeatures
import com.rk.xededitor.ui.screens.settings.mutators.MutatorAPI
import com.rk.xededitor.ui.screens.settings.mutators.Mutators
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object CommandProvider {
    /** Get all registered commands */
    @OptIn(DelicateCoroutinesApi::class)
    @Composable
    fun getAll(): List<Command> = buildList {
        val activity = LocalActivity.current

        // Core application commands
        add(
            Command(
                id = "global.terminal",
                label = stringResource(strings.terminal),
                action = { viewModel ->
                    showTerminalNotice(activity!!) {
                        activity.startActivity(Intent(activity, Terminal::class.java))
                    }
                }
            )
        )

        add(
            Command(
                id = "global.settings",
                label = stringResource(strings.settings),
                action = { viewModel ->
                    activity!!.startActivity(Intent(activity, SettingsActivity::class.java))
                }
            )
        )

        // Core editor commands
        add(
            Command(
                id = "editor.save",
                label = stringResource(strings.save),
                action = { viewModel ->
                    viewModel.currentTab?.let {
                        if (it is EditorTab) {
                            GlobalScope.launch {
                                it.save()
                            }
                        }
                    }
                }
            )
        )

        add(
            Command(
                id = "editor.save_all",
                label = stringResource(strings.save_all),
                action = { viewModel ->
                    viewModel.tabs.forEach {
                        if (it is EditorTab) {
                            GlobalScope.launch {
                                it.save()
                            }
                        }
                    }
                }
            )
        )

        add(
            Command(
                id = "editor.undo",
                label = stringResource(strings.undo),
                action = { viewModel ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    if (currentTab is EditorTab) {
                        currentTab.editorState.editor?.apply {
                            if (canUndo()) {
                                undo()
                            }
                        }
                        currentTab.editorState.editor!!.updateUndoRedo()
                    }
                }
            )
        )

        add(
            Command(
                id = "editor.redo",
                label = stringResource(strings.redo),
                action = { viewModel ->
                    val currentTab = viewModel.tabs[viewModel.currentTabIndex]
                    if (currentTab is EditorTab) {
                        currentTab.editorState.editor?.apply {
                            if (canRedo()) {
                                redo()
                            }
                        }
                        currentTab.editorState.editor!!.updateUndoRedo()
                    }
                }
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
                        action = { viewModel ->
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
                    )
                }
            )
        }
    }

    /** Get a registered command by ID, returns null if not found */
    fun getForId(id: String?, commands: List<Command>): Command? = commands.find { it.id == id }
}