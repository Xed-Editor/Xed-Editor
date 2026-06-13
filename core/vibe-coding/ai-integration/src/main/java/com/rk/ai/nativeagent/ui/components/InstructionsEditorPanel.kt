package com.rk.ai.nativeagent.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

sealed class InstructionsSource {
    data class Project(val path: String) : InstructionsSource()
    data class Global(val path: String) : InstructionsSource()
    data class Claude(val path: String) : InstructionsSource()
    data class Custom(val path: String, val label: String) : InstructionsSource()
}

@Composable
fun InstructionsEditorPanel(
    workspacePath: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources = remember(workspacePath) {
        listOf(
            InstructionsSource.Project("$workspacePath/AGENTS.md"),
            InstructionsSource.Project("$workspacePath/CLAUDE.md"),
            InstructionsSource.Project("$workspacePath/.xed/instructions.md"),
            InstructionsSource.Global("~/.config/xed-editor/AGENTS.md"),
            InstructionsSource.Claude("~/.claude/CLAUDE.md"),
        ).filter {
            when (it) {
                is InstructionsSource.Project -> File(it.path).exists()
                else -> false
            }
        }
    }

    var selectedSource by remember { mutableStateOf<InstructionsSource?>(sources.firstOrNull()) }
    var content by remember(selectedSource) {
        mutableStateOf(selectedSource?.let {
            val path = when (it) {
                is InstructionsSource.Project -> it.path
                is InstructionsSource.Global -> it.path
                is InstructionsSource.Claude -> it.path
                is InstructionsSource.Custom -> it.path
            }
            try {
                File(path).readText()
            } catch (_: Exception) { "" }
        } ?: "")
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Project Instructions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = {
                            selectedSource?.let { src ->
                                val path = when (src) {
                                    is InstructionsSource.Project -> src.path
                                    is InstructionsSource.Global -> src.path
                                    is InstructionsSource.Claude -> src.path
                                    is InstructionsSource.Custom -> src.path
                                }
                                try {
                                    File(path).parentFile?.mkdirs()
                                    File(path).writeText(content)
                                } catch (_: Exception) { }
                            }
                        },
                        enabled = selectedSource != null,
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }
            }

            if (sources.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No instruction files found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Run /init to create AGENTS.md, or create it manually",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = {
                            File("$workspacePath/AGENTS.md").writeText(
                                """
# Project Instructions

## Build Commands

## Test Commands

## Architecture

## Conventions
                                """.trimIndent()
                            )
                            // refresh
                        }) {
                            Text("Create AGENTS.md")
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.weight(1f)) {
                    // Source list (sidebar)
                    Surface(
                        modifier = Modifier.width(180.dp).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            sources.forEach { source ->
                                val path = when (source) {
                                    is InstructionsSource.Project -> source.path
                                    is InstructionsSource.Global -> source.path
                                    is InstructionsSource.Claude -> source.path
                                    is InstructionsSource.Custom -> source.path
                                }
                                val name = path.substringAfterLast("/")
                                val category = when (source) {
                                    is InstructionsSource.Project -> "project"
                                    is InstructionsSource.Global -> "global"
                                    is InstructionsSource.Claude -> "claude"
                                    is InstructionsSource.Custom -> "custom"
                                }

                                Surface(
                                    onClick = {
                                        selectedSource = source
                                        content = File(path).readText()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedSource == source)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                        ) {
                                            Text(
                                                category,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Editor
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Instruction files (AGENTS.md, CLAUDE.md) are loaded as system context.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Precedence: Project AGENTS.md > Project CLAUDE.md > Global ~/.config/xed-editor/AGENTS.md",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
