package com.rk.commands

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.activities.main.MainViewModel
import com.rk.components.XedDialog
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.theme.Typography
import kotlinx.coroutines.DelicateCoroutinesApi

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun CommandPalette(
    progress: Float,
    commands: List<Command>,
    lastUsedCommand: Command?,
    viewModel: MainViewModel,
    onDismissRequest: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val sortedCommands by
        remember(commands, lastUsedCommand) {
            derivedStateOf {
                buildList {
                    lastUsedCommand?.let { add(it) }
                    addAll(commands.filter { it != lastUsedCommand })
                }
            }
        }

    val filteredCommands by
        remember(sortedCommands, searchQuery) {
            derivedStateOf {
                sortedCommands.filter {
                    it.label.value.contains(searchQuery, ignoreCase = true) ||
                        it.description?.contains(searchQuery, ignoreCase = true) == true ||
                        it.prefix?.contains(searchQuery, ignoreCase = true) == true
                }
            }
        }

    val offsetY = with(LocalDensity.current) { (1f - progress) * 100.dp.toPx() }

    XedDialog(
        onDismissRequest = onDismissRequest,
        modifier =
            Modifier.graphicsLayer {
                    this.alpha = progress
                    this.translationY = -offsetY
                }
                .imePadding(),
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                maxLines = 1,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(stringResource(strings.type_command)) },
            )

            LaunchedEffect(progress) { if (progress == 1f) focusRequester.requestFocus() }

            LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
                items(items = filteredCommands, key = { it.id }) { command ->
                    Box(modifier = Modifier.animateItem()) {
                        val isRecentlyUsed = command == lastUsedCommand
                        CommandItem(viewModel, command, isRecentlyUsed, onDismissRequest)
                    }
                }
            }
        }
    }
}

@Composable
fun CommandItem(viewModel: MainViewModel, command: Command, recentlyUsed: Boolean, onDismissRequest: () -> Unit) {
    val activity = LocalActivity.current
    val enabled = command.isSupported.value && command.isEnabled.value

    PreferenceTemplate(
        enabled = enabled,
        modifier =
            Modifier.clickable(
                enabled = enabled,
                onClick = {
                    onDismissRequest()
                    Settings.last_used_command = command.id
                    command.action(viewModel, activity)
                },
            ),
        verticalPadding = 8.dp,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = command.icon.value,
                    contentDescription = command.label.value,
                    modifier = Modifier.padding(end = 8.dp).size(16.dp),
                )

                command.prefix?.let { Text(text = "$it: ", color = MaterialTheme.colorScheme.primary) }
                Text(text = command.label.value)
                if (recentlyUsed) {
                    Text(
                        text = stringResource(strings.recently_used),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        description = { command.description?.let { Text(text = it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        endWidget = {
            command.keybinds?.let {
                Text(
                    text = command.keybinds,
                    fontFamily = FontFamily.Monospace,
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}
