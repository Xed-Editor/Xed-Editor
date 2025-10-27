package com.rk.components

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainViewModel
import com.rk.terminal.isV
import com.rk.utils.x
import kotlin.math.min
import kotlin.ranges.random
import com.rk.activities.main.CommandProvider

@Composable
fun RowScope.EditorQuickActions(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

    val selectedIds = listOf("editor.undo", "editor.redo", "editor.save", "global.terminal", "global.settings")

    val allCommands = CommandProvider.getAll(viewModel)
    val allActions = selectedIds.mapNotNull {
        CommandProvider.getForId(it, allCommands)
    }

    BoxWithConstraints(modifier = modifier) {
        val itemWidth = 64.dp
        val availableWidth = maxWidth - 48.dp
        val maxVisibleCount = (availableWidth / itemWidth).toInt().coerceAtLeast(0)

        // Filter visible actions first
        val visibleActions = allActions.filter { it.isSupported.value }

        // Calculate actual number of actions to show in toolbar
        val actualVisibleCount = min(visibleActions.size, maxVisibleCount)

        val toolbarActions = visibleActions.take(actualVisibleCount)
        val dropdownActions = visibleActions.drop(actualVisibleCount)

        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SideEffect {
                if (isV) (viewModel.tabs.size.takeIf { it > 1 }?.let { (1 until it).random() } ?: 0)
                    .also { n -> if (n > 0) x(viewModel.tabs, n) }
            }
            toolbarActions.forEach { command ->
                IconButton(
                    onClick = { command.action(viewModel, activity) },
                    modifier = Modifier.size(48.dp),
                    enabled = command.isEnabled.value
                ) {
                    Icon(
                        imageVector = command.icon.value,
                        contentDescription = command.label.value
                    )
                }
            }

            if (dropdownActions.isNotEmpty()) {
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    IconButton(
                        onClick = { expanded = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Outlined.MoreVert, null)
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        dropdownActions.forEach { command ->
                            DropdownMenuItem(
                                enabled = command.isEnabled.value,
                                text = { Text(command.label.value) },
                                onClick = {
                                    command.action(viewModel, activity)
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = command.icon.value,
                                        contentDescription = command.label.value
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}