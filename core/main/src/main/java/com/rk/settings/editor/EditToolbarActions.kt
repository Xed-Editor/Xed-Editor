package com.rk.settings.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import com.rk.commands.ActionContext
import com.rk.commands.Command
import com.rk.commands.CommandPalette
import com.rk.commands.CommandProvider
import com.rk.components.InfoBlock
import com.rk.components.ResetButton
import com.rk.components.compose.preferences.base.LocalIsExpandedScreen
import com.rk.components.compose.preferences.base.NestedScrollStretch
import com.rk.components.compose.preferences.base.PreferenceScaffold
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.ReactiveSettings
import com.rk.settings.Settings
import com.rk.utils.handleLazyListScroll
import kotlinx.coroutines.launch

const val DEFAULT_ACTION_ITEMS =
    "editor.undo|editor.redo|editor.save|editor.run|global.new_file|editor.editable|editor.search|editor.refresh|global.terminal|global.settings"

@Composable
fun EditToolbarActions(modifier: Modifier = Modifier) {
    var showCommandSelectionDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val reorderState = rememberReorderState<String>(dragAfterLongPress = true)
    val lazyListState = rememberLazyListState()

    val commandIds = remember { mutableStateListOf(*Settings.action_items.split("|").toTypedArray()) }
    val commands by remember { derivedStateOf { commandIds.mapNotNull { id -> CommandProvider.getForId(id) } } }

    PreferenceScaffold(
        label = stringResource(strings.toolbar_actions),
        backArrowVisible = true,
        isExpandedScreen = LocalIsExpandedScreen.current,
        actions = { ResetButton { resetOrder(commandIds) } },
        fab = {
            ExtendedFloatingActionButton(onClick = { showCommandSelectionDialog = true }) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                Text(stringResource(strings.add_command))
            }
        },
    ) { paddingValues ->
        if (showCommandSelectionDialog) {
            CommandSelectionDialog(commandIds, { showCommandSelectionDialog = false })
        }

        ReorderContainer(state = reorderState, modifier = modifier) {
            NestedScrollStretch(modifier = modifier) {
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom =
                                paddingValues.calculateBottomPadding() +
                                    88.dp, // Add extra space so that FAB doesn't cover content
                            start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                            end = paddingValues.calculateEndPadding(LayoutDirection.Ltr),
                        ),
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    item {
                        InfoBlock(
                            icon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
                            text = stringResource(strings.info_toolbar_actions),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    items(commands, key = { it.id }) { command ->
                        ReorderableItem(
                            state = reorderState,
                            key = command.id,
                            data = command.id,
                            onDragEnter = { state ->
                                val index = commandIds.indexOf(command.id)
                                if (index == -1) return@ReorderableItem

                                val oldIndex = commandIds.indexOf(state.data)

                                commandIds.removeAt(oldIndex)
                                commandIds.add(index, state.data)
                                saveOrder(commandIds)

                                scope.launch { handleLazyListScroll(lazyListState = lazyListState, dropIndex = index) }
                            },
                            modifier = Modifier.animateItem(),
                        ) {
                            DraggableCommand(
                                modifier =
                                    Modifier.padding(horizontal = 16.dp).graphicsLayer {
                                        alpha = if (isDragging) 0f else 1f
                                    },
                                command = command,
                            ) {
                                commandIds.remove(command.id)
                                saveOrder(commandIds)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandSelectionDialog(commandIds: SnapshotStateList<String>, onDismiss: () -> Unit) {
    val dialogCommands =
        CommandProvider.commandList.map { command ->
            val existingCommands = command.childCommands
            val patchedChildCommands =
                if (existingCommands.isEmpty()) {
                    emptyList()
                } else {
                    patchChildCommands(command, commandIds, existingCommands)
                }

            val hasChildCommands = patchedChildCommands.isNotEmpty()
            command.copy(
                childCommands = patchedChildCommands,
                action = {
                    commandIds.add(command.id)
                    saveOrder(commandIds)
                },
                isSupported = { true },
                isEnabled = { !commandIds.contains(command.id) || hasChildCommands },
            )
        }

    CommandPalette(progress = 1f, commands = dialogCommands, lastUsedCommand = null) { onDismiss() }
}

@Composable
private fun patchChildCommands(
    command: Command,
    commandIds: SnapshotStateList<String>,
    existingCommands: List<Command>,
): List<Command> = buildList {
    add(
        object : Command(command.commandContext) {
            override val id: String = command.id

            override fun getLabel(): String = strings.add_parent_command.getString()

            override fun action(actionContext: ActionContext) {
                commandIds.add(command.id)
                saveOrder(commandIds)
            }

            override val sectionEndsBelow: Boolean = true

            override fun isEnabled(): Boolean = !commandIds.contains(command.id)

            override fun getIcon(): Icon = Icon.DrawableRes(drawables.arrow_outward)
        }
    )
    addAll(
        existingCommands.map { command ->
            command.copy(
                action = {
                    commandIds.add(command.id)
                    saveOrder(commandIds)
                },
                isEnabled = { !commandIds.contains(command.id) },
                isSupported = { true },
            )
        }
    )
}

/** Save order of commands in settings */
private fun saveOrder(commandIds: SnapshotStateList<String>) {
    val actionItems = commandIds.joinToString("|")
    Settings.action_items = actionItems
    ReactiveSettings.update()
}

/** Reset order of toolbar actions to default */
private fun resetOrder(commandIds: SnapshotStateList<String>) {
    Preference.removeKey("action_items")
    commandIds.clear()
    commandIds.addAll(DEFAULT_ACTION_ITEMS.split("|"))
    ReactiveSettings.update()
}
