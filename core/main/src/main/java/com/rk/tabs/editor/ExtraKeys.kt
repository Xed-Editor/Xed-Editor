package com.rk.tabs.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainActivity
import com.rk.commands.ActionContext
import com.rk.commands.CommandProvider
import com.rk.icons.Icon
import com.rk.settings.ReactiveSettings

private data class ExtraKey(val label: String, val icon: Icon? = null, val enabled: Boolean, val onClick: () -> Unit)

@Composable
fun ExtraKeys(editorTab: EditorTab) {
    val commandIds by remember { derivedStateOf { ReactiveSettings.extraKeyCommandIds.split("|").toTypedArray() } }
    val commands by remember { derivedStateOf { commandIds.mapNotNull { id -> CommandProvider.getForId(id) } } }

    val commandExtraKeys =
        commands.map { command ->
            ExtraKey(
                label = command.getLabel(),
                icon = command.getIcon(),
                enabled = command.isEnabled() && command.isSupported(),
                onClick = { command.performCommand(ActionContext(MainActivity.instance!!)) },
            )
        }

    val isEditable by remember { derivedStateOf { editorTab.editorState.editable } }
    val symbols = ReactiveSettings.extraKeySymbols
    val symbolExtraKeys =
        symbols.map {
            ExtraKey(
                label = it.toString(),
                icon = null,
                enabled = isEditable,
                onClick = {
                    val editor = editorTab.editorState.editor.get() ?: return@ExtraKey
                    val insertChar = it.toString()

                    if (insertChar == "\t") {
                        if (editor.snippetController.isInSnippet()) {
                            editor.snippetController.shiftToNextTabStop()
                        } else {
                            editor.indentOrCommitTab()
                        }
                    } else {
                        editor.insertText(insertChar, 1)
                    }
                },
            )
        }

    val extraKeys = commandExtraKeys + symbolExtraKeys

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ReactiveSettings.splitExtraKeys) {
            KeyRow(commandExtraKeys)
            KeyRow(symbolExtraKeys)
        } else {
            KeyRow(extraKeys)
        }
    }
}

@Composable
private fun KeyRow(extraKeys: List<ExtraKey>) {
    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(extraKeys, key = { it.label }) { KeyButton(it) }
    }
}

@Composable
private fun KeyButton(key: ExtraKey) {
    val hapticFeedback = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(32.dp, 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (ReactiveSettings.extraKeysBackground) {
                        Modifier.background(
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (key.enabled) 1f else 0.5f)
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    enabled = key.enabled,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        key.onClick()
                    },
                ),
    ) {
        when (val icon = key.icon) {
            is Icon.DrawableRes -> {
                Icon(
                    painter = painterResource(id = icon.drawableRes),
                    contentDescription = key.label,
                    modifier = Modifier.size(16.dp),
                    tint =
                        if (key.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            is Icon.VectorIcon -> {
                Icon(
                    imageVector = icon.vector,
                    contentDescription = key.label,
                    modifier = Modifier.size(16.dp),
                    tint =
                        if (key.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            else -> {
                Text(
                    text = key.label,
                    fontFamily = FontFamily.Monospace,
                    color =
                        if (key.enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        },
                    maxLines = 1,
                )
            }
        }
    }
}
