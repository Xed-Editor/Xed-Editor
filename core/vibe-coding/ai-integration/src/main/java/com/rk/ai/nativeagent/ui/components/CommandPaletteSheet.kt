package com.rk.ai.nativeagent.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.ai.agent.files.CommandDefinition

data class PaletteCommand(
    val id: String,
    val name: String,
    val description: String,
    val prompt: String,
    val category: String = "General",
    val icon: @Composable () -> Unit = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
)

fun CommandDefinition.toPaletteCommand(): PaletteCommand {
    val icon: @Composable () -> Unit = when (category.lowercase()) {
        "git" -> { { Icon(Icons.Outlined.Code, contentDescription = null) } }
        "code" -> { { Icon(Icons.Outlined.BugReport, contentDescription = null) } }
        "feature" -> { { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) } }
        "project" -> { { Icon(Icons.Outlined.Folder, contentDescription = null) } }
        else -> { { Icon(Icons.Outlined.Terminal, contentDescription = null) } }
    }
    return PaletteCommand(
        id = id,
        name = name,
        description = description,
        prompt = prompt,
        category = category,
        icon = icon,
    )
}

@Composable
fun CommandPaletteSheet(
    builtinCommands: List<PaletteCommand>,
    fileCommands: List<PaletteCommand>,
    onDismiss: () -> Unit,
    onExecuteCommand: (PaletteCommand) -> Unit,
    onRefreshCommands: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var showBuiltins by remember { mutableStateOf(true) }
    var showFileCommands by remember { mutableStateOf(true) }

    val allCommands = remember(builtinCommands, fileCommands) {
        builtinCommands.map { it.copy(category = "${it.category} (built-in)") } +
            fileCommands.map { it.copy(category = "${it.category} (custom)") }
    }

    val filteredCommands = remember(searchQuery, allCommands) {
        if (searchQuery.isBlank()) allCommands
        else allCommands.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    val categories = remember(filteredCommands) {
        filteredCommands.map { it.category }.distinct()
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Terminal, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Command Palette",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (onRefreshCommands != null) {
                            IconButton(onClick = onRefreshCommands) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh commands")
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search commands...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    if (fileCommands.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${fileCommands.size} custom commands available from .xed/commands/",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                categories.forEach { category ->
                    item {
                        Text(
                            category,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }

                    val catCommands = filteredCommands.filter { it.category == category }
                    items(catCommands) { command ->
                        Surface(
                            onClick = { onExecuteCommand(command) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Box(modifier = Modifier.padding(6.dp)) {
                                        command.icon()
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "/${command.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        command.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
