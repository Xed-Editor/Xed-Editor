package com.rk.tabs.editor

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainViewModel
import com.rk.commands.ActionContext
import com.rk.commands.CommandProvider
import com.rk.commands.KeybindingsManager
import com.rk.icons.Icon
import com.rk.settings.ReactiveSettings
import com.rk.terminal.isV
import com.rk.theme.Typography
import com.rk.utils.x
import kotlin.math.min

@Composable
fun RowScope.EditorActions(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

    val allActions = ReactiveSettings.toolbarActionIds.split("|").mapNotNull { CommandProvider.getForId(it) }

    BoxWithConstraints(modifier = modifier) {
        val itemWidth = 64.dp
        val availableWidth = maxWidth - 48.dp
        val maxVisibleCount = (availableWidth / itemWidth).toInt().coerceAtLeast(0)

        // Filter visible actions first
        val visibleActions = allActions.filter { it.isSupported() }

        // Calculate actual number of actions to show in toolbar
        var actualVisibleCount = min(visibleActions.size, maxVisibleCount)

        // Make sure that the dropdown menu never contains only one entry
        if (visibleActions.size - actualVisibleCount == 1) {
            actualVisibleCount += 1
        }

        val toolbarActions = visibleActions.take(actualVisibleCount)
        val dropdownActions = visibleActions.drop(actualVisibleCount)

        Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            SideEffect {
                if (isV)
                    (viewModel.tabs.size.takeIf { it > 1 }?.let { (1 until it).random() } ?: 0).also { n ->
                        if (n > 0) x(viewModel.tabs, n)
                    }
            }
            toolbarActions.forEach { command ->
                IconButton(
                    onClick = { command.performCommand(ActionContext(activity!!)) },
                    modifier = Modifier.size(48.dp),
                    enabled = command.isEnabled(),
                ) {
                    when (val icon = command.getIcon()) {
                        is Icon.DrawableRes -> {
                            Icon(
                                painter = painterResource(id = icon.drawableRes),
                                contentDescription = command.getLabel(),
                            )
                        }

                        is Icon.VectorIcon -> {
                            Icon(imageVector = icon.vector, contentDescription = command.getLabel())
                        }
                    }
                }
            }

            if (dropdownActions.isNotEmpty()) {
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.MoreVert, null)
                    }

                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        dropdownActions.forEach { command ->
                            DropdownMenuItem(
                                enabled = command.isEnabled(),
                                text = { Text(command.getLabel()) },
                                onClick = {
                                    command.performCommand(ActionContext(activity!!))
                                    expanded = false
                                },
                                leadingIcon = {
                                    when (val icon = command.getIcon()) {
                                        is Icon.DrawableRes -> {
                                            Icon(
                                                painter = painterResource(id = icon.drawableRes),
                                                contentDescription = command.getLabel(),
                                            )
                                        }

                                        is Icon.VectorIcon -> {
                                            Icon(imageVector = icon.vector, contentDescription = command.getLabel())
                                        }
                                    }
                                },
                                trailingIcon = {
                                    val keyCombination = KeybindingsManager.getKeyCombinationForCommand(command.id)
                                    val displayKeyCombination = keyCombination?.getDisplayName()
                                    displayKeyCombination?.let {
                                        Text(
                                            modifier = Modifier.padding(start = 4.dp),
                                            text = it,
                                            maxLines = 1,
                                            fontFamily = FontFamily.Monospace,
                                            style = Typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
