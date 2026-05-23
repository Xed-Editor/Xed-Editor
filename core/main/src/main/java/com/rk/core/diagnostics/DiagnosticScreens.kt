package com.rk.core.diagnostics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConsoleScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logs = remember { mutableStateListOf<LogEntry>() }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val listener: (LogEntry) -> Unit = { entry ->
            logs.add(0, entry)
            if (logs.size > 500) logs.removeAt(logs.lastIndex)
        }
        DebugConsole.onLog(listener)
        logs.addAll(DebugConsole.getRecentLogs(100).reversed())
        onDispose { DebugConsole.removeListener(listener) }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Debug Console") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "Close")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val diag = DebugConsole.systemDiagnostic()
                            DebugConsole.log(LogLevel.INFO, "Diagnostic", diag)
                        }
                    }) {
                        Icon(Icons.Outlined.MedicalServices, "Diagnostics")
                    }
                    IconButton(onClick = { DebugConsole.clear(); logs.clear() }) {
                        Icon(Icons.Outlined.Clear, "Clear")
                    }
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LogLevel.entries.forEach { level ->
                    FilterChip(
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = if (selectedLevel == level) null else level },
                        label = { Text(level.name, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = levelColor(level).copy(alpha = 0.2f),
                        ),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(logs.filter { entry ->
                    (selectedLevel == null || entry.level == selectedLevel)
                }) { entry ->
                    LogEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val dateFormat = remember { java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp),
        color = levelColor(entry.level).copy(alpha = 0.05f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = dateFormat.format(java.util.Date(entry.timestamp)),
                color = Color(0xFF9E9E9E),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.width(80.dp),
            )
            Text(
                text = "[${entry.level.name.first()}]",
                color = levelColor(entry.level),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.width(32.dp),
            )
            Text(
                text = entry.tag,
                color = Color(0xFF64B5F6),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(100.dp),
            )
            Text(
                text = entry.message,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
                maxLines = 5,
            )
        }
    }
}

@Composable
fun SystemDiagnosticCard(modifier: Modifier = Modifier) {
    val diag = remember { mutableStateOf("Loading...") }

    LaunchedEffect(Unit) {
        diag.value = DebugConsole.systemDiagnostic()
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("System Diagnostic", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = diag.value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.ERROR -> Color(0xFFEF5350)
    LogLevel.WARN -> Color(0xFFFFC107)
    LogLevel.INFO -> Color(0xFF42A5F5)
    LogLevel.DEBUG -> Color(0xFF9E9E9E)
}
