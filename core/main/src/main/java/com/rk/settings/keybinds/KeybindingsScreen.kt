package com.rk.settings.keybinds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.commands.Command
import com.rk.commands.CommandProvider
import com.rk.commands.KeyAction
import com.rk.commands.KeyCombination
import com.rk.commands.KeybindingsManager
import com.rk.components.InfoBlock
import com.rk.components.ResetButton
import com.rk.components.compose.preferences.base.PreferenceLayoutLazyColumn
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.theme.Typography

@Composable
fun KeybindingsScreen() {
    var editCommandKeybinds by remember { mutableStateOf<Command?>(null) }
    val commands = CommandProvider.globalCommands
    var refreshTrigger by remember { mutableIntStateOf(0) }

    PreferenceLayoutLazyColumn(label = stringResource(id = strings.keybindings), backArrowVisible = true) {
        item { InfoBlock(text = stringResource(id = strings.keybinds_info)) }

        item {
            Row {
                Spacer(modifier = Modifier.weight(1f))
                ResetButton {
                    KeybindingsManager.resetAllKeys()
                    refreshTrigger++
                }
            }
        }

        items(commands) { command ->
            val keyCombination by
                remember(refreshTrigger) {
                    derivedStateOf { KeybindingsManager.getKeyCombinationForCommand(command.id) }
                }
            KeybindItem(command = command, keyCombination = keyCombination) { editCommandKeybinds = it }
        }
    }

    editCommandKeybinds?.let { command ->
        EditKeybindsDialog(
            command = command,
            onSubmit = { keyCombination ->
                if (keyCombination == null) {
                    KeybindingsManager.resetCustomKey(command.id)
                    refreshTrigger++
                    return@EditKeybindsDialog
                }
                KeybindingsManager.editCustomKey(KeyAction(commandId = command.id, keyCombination = keyCombination))
                refreshTrigger++
            },
            onDismiss = { editCommandKeybinds = null },
        )
    }
}

@Composable
fun EditKeybindsDialog(command: Command, onSubmit: (KeyCombination?) -> Unit, onDismiss: () -> Unit) {
    val noneSetText = stringResource(strings.none_set)

    val currentKeyCombination = KeybindingsManager.getKeyCombinationForCommand(command.id)
    var keyCombination by remember { mutableStateOf(currentKeyCombination) }
    val keyCombinationText = keyCombination?.getDisplayName() ?: noneSetText

    var textFieldValue by
        remember(keyCombinationText) {
            mutableStateOf(TextFieldValue(text = keyCombinationText, selection = TextRange(keyCombinationText.length)))
        }

    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = command.label.value) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(strings.press_key))
                TextField(
                    modifier =
                        Modifier.focusRequester(focusRequester).onPreviewKeyEvent { keyEvent ->
                            // Do not allow only modifier key as shortcut (Ctrl, Alt and Shift has to be combined)
                            if (KeyUtils.isModifierKey(keyEvent)) {
                                return@onPreviewKeyEvent true
                            }

                            keyCombination = KeyCombination.fromEvent(keyEvent)
                            return@onPreviewKeyEvent true
                        },
                    value = textFieldValue,
                    onValueChange = {
                        it.text.lastOrNull()?.let { char ->
                            val keyCode = KeyUtils.getKeyCodeFromChar(char)
                            keyCombination =
                                keyCombination?.copy(keyCode = keyCode) ?: KeyCombination(keyCode = keyCode)
                        }
                    },
                    trailingIcon = {
                        Icon(
                            modifier = Modifier.combinedClickable(onClick = { keyCombination = null }),
                            painter = painterResource(drawables.close),
                            contentDescription = null,
                        )
                    },
                    maxLines = 1,
                    textStyle = Typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                )

                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        enabled = keyCombination != null,
                        checked = keyCombination?.ctrl == true,
                        onCheckedChange = { keyCombination = keyCombination?.let { it.copy(ctrl = !it.ctrl) } },
                    )
                    Text(stringResource(strings.ctrl))

                    Spacer(modifier = Modifier.width(8.dp))

                    Checkbox(
                        enabled = keyCombination != null,
                        checked = keyCombination?.shift == true,
                        onCheckedChange = { keyCombination = keyCombination?.let { it.copy(shift = !it.shift) } },
                    )
                    Text(stringResource(strings.shift))

                    Spacer(modifier = Modifier.width(8.dp))

                    Checkbox(
                        enabled = keyCombination != null,
                        checked = keyCombination?.alt == true,
                        onCheckedChange = { keyCombination = keyCombination?.let { it.copy(alt = !it.alt) } },
                    )
                    Text(stringResource(strings.alt))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(keyCombination)
                    onDismiss()
                }
            ) {
                Text(stringResource(strings.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
    )
}

@Composable
fun KeybindItem(command: Command, keyCombination: KeyCombination?, promptKeybinds: (Command) -> Unit) {
    PreferenceTemplate(
        modifier = Modifier.clickable(enabled = !command.externalKeybind, onClick = { promptKeybinds(command) }),
        verticalPadding = 8.dp,
        horizontalPadding = 24.dp,
        enabled = !command.externalKeybind,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                when (val icon = command.icon.value) {
                    is Icon.DrawableRes -> {
                        Icon(
                            painter = painterResource(id = icon.drawableRes),
                            contentDescription = command.label.value,
                            modifier = Modifier.padding(end = 8.dp).size(16.dp),
                        )
                    }

                    is Icon.VectorIcon -> {
                        Icon(
                            imageVector = icon.vector,
                            contentDescription = command.label.value,
                            modifier = Modifier.padding(end = 8.dp).size(16.dp),
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        command.prefix?.let { Text(text = "$it: ", color = MaterialTheme.colorScheme.primary) }
                        Text(
                            text = command.label.value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        endWidget = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = keyCombination?.getDisplayName() ?: stringResource(strings.none_set),
                    fontFamily = FontFamily.Monospace,
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}
