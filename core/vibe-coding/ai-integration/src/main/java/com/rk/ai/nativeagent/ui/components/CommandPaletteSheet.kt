package com.rk.ai.nativeagent.ui.components

import androidx.compose.foundation.clickable
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

data class PaletteCommand(
    val id: String,
    val name: String,
    val description: String,
    val prompt: String,
    val category: String = "General",
    val icon: @Composable () -> Unit = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
)

@Composable
fun CommandPaletteSheet(
    onDismiss: () -> Unit,
    onExecuteCommand: (PaletteCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }

    val commands = remember {
        listOf(
            PaletteCommand("init", "Init", "Initialize project instructions (AGENTS.md)", "Initialize project with AGENTS.md based on codebase analysis", "Project"),
            PaletteCommand("review", "Review", "Review recent code changes", "Review all uncommitted changes for bugs and quality", "Code"),
            PaletteCommand("test", "Test", "Run tests and analyze results", "Run the test suite and report failures with fix suggestions", "Code"),
            PaletteCommand("commit", "Commit", "Stage and commit changes", "Stage all changes and create a descriptive commit", "Git"),
            PaletteCommand("push", "Push", "Push commits to remote", "Push the current branch to origin", "Git"),
            PaletteCommand("changelog", "Changelog", "Generate changelog from recent commits", "Generate a changelog file from git history", "Project"),
            PaletteCommand("spellcheck", "Spell Check", "Check spelling in markdown files", "Run spell check on all changed markdown files", "Code"),
            PaletteCommand("translate", "Translate", "Translate documentation", "Translate changed documentation to configured languages", "Project"),
            PaletteCommand("summarize", "Summarize", "Summarize current conversation", "Create a summary of the conversation context", "General"),
            PaletteCommand("compact", "Compact", "Compact conversation context", "Compact the conversation to free context window", "General"),
            PaletteCommand("learn", "Learn", "Extract learnings to AGENTS.md", "Analyze session and extract non-obvious learnings", "Project"),
            PaletteCommand("rmslop", "Remove Slop", "Remove AI-generated code slop", "Clean up unnecessary comments and defensive code", "Code"),
            PaletteCommand("issues", "Issues", "Find matching GitHub issues", "Search GitHub issues matching the current context", "Git"),
        )
    }

    val filteredCommands = remember(searchQuery) {
        if (searchQuery.isBlank()) commands
        else commands.filter {
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
