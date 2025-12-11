package com.rk.commands

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
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
    initialChildCommands: List<Command>? = null,
    initialPlaceholder: String? = null,
    onDismissRequest: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    var childCommands by remember { mutableStateOf(initialChildCommands) }
    var placeholderOverride by remember { mutableStateOf(initialPlaceholder) }

    val sortedCommands by
        remember(commands, lastUsedCommand) {
            derivedStateOf {
                buildList {
                    lastUsedCommand?.let { add(it) }
                    addAll(commands.filter { it != lastUsedCommand })
                }
            }
        }

    val visibleCommands = childCommands ?: sortedCommands

    val filteredCommands by
        remember(visibleCommands, searchQuery) {
            derivedStateOf {
                visibleCommands.filter {
                    it.label.value.contains(searchQuery, ignoreCase = true) ||
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
        BackHandler(enabled = childCommands != null && initialChildCommands == null) {
            childCommands = null
            placeholderOverride = null
            searchQuery = ""
        }

        Column(modifier = Modifier.animateContentSize()) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                maxLines = 1,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = {
                    Text(
                        if (childCommands != null && placeholderOverride != null) {
                            placeholderOverride!!
                        } else {
                            stringResource(strings.type_command)
                        }
                    )
                },
            )

            LaunchedEffect(progress) { if (progress == 1f) focusRequester.requestFocus() }

            AnimatedContent(
                targetState = childCommands != null,
                transitionSpec = {
                    if (targetState) {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) togetherWith
                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left)
                    } else {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right) togetherWith
                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right)
                    }
                },
            ) { isSubpage ->
                LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
                    items(items = filteredCommands, key = { it.id }) { command ->
                        Box(modifier = Modifier.animateItem()) {
                            val isRecentlyUsed = command == lastUsedCommand
                            CommandItem(
                                viewModel,
                                command,
                                isRecentlyUsed,
                                onDismissRequest,
                                onNavigateToChildren = { placeholder, commands ->
                                    childCommands = commands
                                    placeholderOverride = placeholder
                                    searchQuery = ""
                                },
                                isSubpage = isSubpage,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommandItem(
    viewModel: MainViewModel,
    command: Command,
    recentlyUsed: Boolean,
    onDismissRequest: () -> Unit,
    onNavigateToChildren: (String?, List<Command>) -> Unit,
    isSubpage: Boolean,
) {
    val activity = LocalActivity.current
    val enabled = command.isSupported.value && command.isEnabled.value
    val childCommands = command.childCommands

    Column {
        PreferenceTemplate(
            enabled = enabled,
            modifier =
                Modifier.clickable(
                    enabled = enabled,
                    onClick = {
                        Settings.last_used_command = command.id
                        if (childCommands.isNotEmpty()) {
                            onNavigateToChildren(command.childSearchPlaceholder, childCommands)
                        } else {
                            onDismissRequest()
                            command.action(viewModel, activity)
                        }
                    },
                ),
            verticalPadding = 8.dp,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val icon = command.icon.value
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = command.label.value,
                        modifier = Modifier.padding(end = 8.dp).size(16.dp),
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            command.prefix?.let { Text(text = "$it: ", color = MaterialTheme.colorScheme.primary) }
                            Text(
                                text = command.label.value,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (recentlyUsed) {
                                Text(
                                    text = stringResource(strings.recently_used),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }

                        if (!isSubpage) {
                            CommandProvider.getParentCommand(command)?.label?.let {
                                Text(
                                    text = it.value,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            },
            endWidget = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    command.keybinds?.let {
                        Text(
                            text = command.keybinds,
                            fontFamily = FontFamily.Monospace,
                            style = Typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (childCommands.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.height(16.dp),
                        )
                    }
                }
            },
        )

        if (command.sectionEndsBelow) {
            HorizontalDivider()
        }
    }
}
