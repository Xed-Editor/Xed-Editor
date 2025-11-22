package com.rk.settings.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import com.rk.activities.main.MainActivity
import com.rk.commands.Command
import com.rk.commands.CommandPalette
import com.rk.commands.CommandProvider
import com.rk.components.InfoBlock
import com.rk.components.compose.preferences.base.LocalIsExpandedScreen
import com.rk.components.compose.preferences.base.NestedScrollStretch
import com.rk.components.compose.preferences.base.PreferenceScaffold
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.Settings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun ToolbarActions(modifier: Modifier = Modifier) {
    var showCommandSelectionDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val reorderState = rememberReorderState<String>(dragAfterLongPress = true)
    val lazyListState = rememberLazyListState()

    val allCommands = MainActivity.instance!!.viewModel.commands
    val commandIds = remember { mutableStateListOf(*Settings.action_items.split("|").toTypedArray()) }
    val commands = commandIds.mapNotNull { id -> CommandProvider.getForId(id, allCommands) }

    PreferenceScaffold(
        label = stringResource(strings.toolbar_actions),
        backArrowVisible = true,
        isExpandedScreen = LocalIsExpandedScreen.current,
        fab = {
            ExtendedFloatingActionButton(onClick = { showCommandSelectionDialog = true }) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                Text(stringResource(strings.add_command))
            }
        },
    ) { paddingValues ->
        if (showCommandSelectionDialog) {
            val commands =
                MainActivity.instance!!.viewModel.commands.map {
                    Command(
                        id = it.id,
                        prefix = it.prefix,
                        label = it.label,
                        description = it.description,
                        action = { _, _ ->
                            commandIds.add(it.id)

                            // Save order in settings
                            Settings.action_items = commandIds.joinToString("|")
                        },
                        isEnabled = derivedStateOf { !commandIds.contains(it.id) },
                        isSupported = mutableStateOf(true),
                        icon = it.icon,
                        keybinds = it.keybinds,
                    )
                }

            CommandPalette(
                progress = 1f,
                commands = commands,
                lastUsedCommand = null,
                viewModel = MainActivity.instance!!.viewModel,
            ) {
                showCommandSelectionDialog = false
            }
        }

        ReorderContainer(state = reorderState, modifier = modifier) {
            NestedScrollStretch(modifier = modifier) {
                LazyColumn(
                    contentPadding = paddingValues,
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxHeight().padding(bottom = 8.dp),
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
                            key = command,
                            data = command.id,
                            onDrop = {},
                            onDragEnter = { state ->
                                val index = commandIds.indexOf(command.id)
                                if (index == -1) return@ReorderableItem

                                val oldIndex = commandIds.indexOf(state.data)

                                commandIds.removeAt(oldIndex)
                                commandIds.add(index, state.data)

                                // Save order in settings
                                Settings.action_items = commandIds.joinToString("|")

                                scope.launch { handleLazyListScroll(lazyListState = lazyListState, dropIndex = index) }
                            },
                            modifier = Modifier.animateItem(),
                        ) {
                            ActionItem(
                                modifier =
                                    Modifier.padding(horizontal = 16.dp).graphicsLayer {
                                        alpha = if (isDragging) 0f else 1f
                                    },
                                command = command,
                            ) {
                                commandIds.remove(command.id)

                                // Save order in settings
                                Settings.action_items = commandIds.joinToString("|")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionItem(modifier: Modifier = Modifier, command: Command, onRemove: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp, modifier = modifier) {
        PreferenceTemplate(
            modifier = Modifier.clickable {},
            verticalPadding = 8.dp,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ImageVector.vectorResource(drawables.drag_indicator),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 12.dp).size(20.dp),
                    )

                    Icon(
                        imageVector = command.icon.value,
                        contentDescription = command.label.value,
                        modifier = Modifier.padding(end = 8.dp).size(20.dp),
                    )

                    command.prefix?.let { Text(text = "$it: ", color = MaterialTheme.colorScheme.primary) }
                    Text(text = command.label.value, style = MaterialTheme.typography.bodyLarge)
                }
            },
            description = {
                command.description?.let { Text(text = it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
            endWidget = { IconButton(onClick = { onRemove() }) { Icon(imageVector = Icons.Outlined.Delete, null) } },
        )
    }
}

// Helper function copied from
// https://github.com/MohamedRejeb/compose-dnd/blob/65d48ed0f0bd83a0b01263b7e046864bdd4a9048/sample/common/src/commonMain/kotlin/utils/ScrollUtils.kt
suspend fun handleLazyListScroll(lazyListState: LazyListState, dropIndex: Int): Unit = coroutineScope {
    val firstVisibleItemIndex = lazyListState.firstVisibleItemIndex
    val firstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset

    // Workaround to fix scroll issue when dragging the first item
    if (dropIndex == 0 || dropIndex == 1) {
        launch { lazyListState.scrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset) }
    }

    // Animate scroll when entering the first or last item
    val lastVisibleItemIndex = lazyListState.firstVisibleItemIndex + lazyListState.layoutInfo.visibleItemsInfo.lastIndex

    val firstVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return@coroutineScope
    val scrollAmount = firstVisibleItem.size * 2f

    if (dropIndex <= firstVisibleItemIndex + 1) {
        launch { lazyListState.animateScrollBy(-scrollAmount) }
    } else if (dropIndex == lastVisibleItemIndex) {
        launch { lazyListState.animateScrollBy(scrollAmount) }
    }
}
