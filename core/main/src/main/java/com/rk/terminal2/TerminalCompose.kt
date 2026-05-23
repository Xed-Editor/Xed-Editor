package com.rk.terminal2

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TerminalLine(
    val content: String,
    val isInput: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

@Composable
fun ModernTerminalView(
    lines: List<TerminalLine>,
    onCommand: (String) -> Unit,
    isConnected: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val color = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxSize()) {
        TerminalToolbar(
            isConnected = isConnected,
            onClear = onClear,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1E1E1E))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(lines) { line ->
                TerminalLineItem(line)
            }
        }

        TerminalInputBar(
            value = input,
            onValueChange = { input = it },
            onSend = {
                val text = input.text.trim()
                if (text.isNotBlank()) {
                    onCommand(text)
                    input = TextFieldValue("")
                }
            },
            isConnected = isConnected,
        )
    }
}

@Composable
private fun TerminalToolbar(
    isConnected: Boolean,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (isConnected) Color(0xFF4CAF50) else Color(0xFFEF5350),
                        RoundedCornerShape(4.dp),
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isConnected) "Connected" else "Disconnected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${lines.size} lines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Delete, "Clear", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TerminalLineItem(line: TerminalLine) {
    val bg = if (line.isInput) Color(0xFF2D2D2D) else Color.Transparent
    val prefix = if (line.isInput) "$ " else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = prefix + line.content,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = if (line.isInput) Color(0xFF89DDFF) else Color(0xFFD4D4D4),
            ),
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun TerminalInputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    isConnected: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF252526),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFF89DDFF),
                ),
                modifier = Modifier.padding(end = 4.dp),
            )

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFD4D4D4),
                ),
                cursorBrush = SolidColor(Color(0xFFD4D4D4)),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.text.isEmpty()) {
                            Text(
                                "Type a command...",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = Color(0xFF6A6A6A),
                                ),
                            )
                        }
                        innerTextField()
                    }
                },
            )

            IconButton(
                onClick = onSend,
                enabled = isConnected && value.text.isNotBlank(),
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Outlined.Send, "Send", modifier = Modifier.size(18.dp))
            }
        }
    }
}
