package com.rk.ai.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rk.resources.strings
import com.rk.theme.gitAdded

@Composable
fun AiDiffView(diff: String, modifier: Modifier = Modifier) {
    val lines = diff.lines()
    val shown = lines.take(MAX_DIFF_LINES)
    val addedColor = MaterialTheme.colorScheme.gitAdded
    val removedColor = MaterialTheme.colorScheme.error
    val headerColor = MaterialTheme.colorScheme.onSurfaceVariant
    val contextColor = MaterialTheme.colorScheme.onSurface

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                shown.forEach { line ->
                    val color =
                        when {
                            line.startsWith("+++") || line.startsWith("---") -> headerColor
                            line.startsWith("@@") -> MaterialTheme.colorScheme.primary
                            line.startsWith("+") -> addedColor
                            line.startsWith("-") -> removedColor
                            else -> contextColor
                        }
                    Text(
                        text = line.ifEmpty { " " },
                        color = color,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    )
                }
                if (lines.size > shown.size) {
                    Text(
                        text = stringResource(strings.ai_diff_more_lines, lines.size - shown.size),
                        color = headerColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private const val MAX_DIFF_LINES = 250
