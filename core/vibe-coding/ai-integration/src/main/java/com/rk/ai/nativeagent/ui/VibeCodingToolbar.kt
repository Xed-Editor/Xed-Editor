@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.nativeagent.engine.VibeCodingState
import kotlin.uuid.ExperimentalUuidApi

private enum class OverflowAction {
    SKILLS, AGENTS, RULES, PLUGINS, PERMS, CLEAR, EXPORT
}

@Composable
internal fun VibeCodingToolbar(
    engine: VibeCodingEngine,
    state: VibeCodingState,
    colorScheme: ColorScheme,
    showFiles: Boolean,
    showHistory: Boolean,
    showSuggestions: Boolean,
    showAgentActivity: Boolean,
    selectedInfoTab: InfoTab?,
    onToggleFiles: () -> Unit,
    onToggleHistory: () -> Unit,
    onToggleSuggestions: () -> Unit,
    onToggleAgentActivity: () -> Unit,
    onSelectInfoTab: (InfoTab) -> Unit,
    onOpenPanel: (ToolPanel) -> Unit,
    onShowClearDialog: () -> Unit,
    onShowExportDialog: () -> Unit,
    onSettings: () -> Unit,
) {
    var showOverflow by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainerLow,
        tonalElevation = 0.5.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Model indicator
                ModelBadge(engine = engine, colorScheme = colorScheme)

                ToolbarDivider(colorScheme)

                // Primary navigation
                ToolbarChip(
                    icon = Icons.Outlined.Terminal,
                    label = "Commands",
                    onClick = { onOpenPanel(ToolPanel.COMMANDS) },
                    colorScheme = colorScheme,
                )
                ToolbarChip(
                    icon = Icons.Outlined.Code,
                    label = "Files",
                    isActive = showFiles,
                    onClick = onToggleFiles,
                    colorScheme = colorScheme,
                )
                ToolbarChip(
                    icon = Icons.Outlined.List,
                    label = "History",
                    isActive = showHistory,
                    onClick = onToggleHistory,
                    colorScheme = colorScheme,
                )

                ToolbarDivider(colorScheme)

                // Info panel toggles
                ToolbarToggleButton(
                    icon = Icons.Outlined.Checklist,
                    label = "Plan",
                    isActive = selectedInfoTab == InfoTab.PLAN,
                    onClick = { onSelectInfoTab(InfoTab.PLAN) },
                    colorScheme = colorScheme,
                )
                ToolbarToggleButton(
                    icon = Icons.Outlined.Build,
                    label = "Tools",
                    isActive = selectedInfoTab == InfoTab.TOOLS,
                    onClick = { onSelectInfoTab(InfoTab.TOOLS) },
                    colorScheme = colorScheme,
                )
                ToolbarToggleButton(
                    icon = Icons.Outlined.Info,
                    label = "Context",
                    isActive = selectedInfoTab == InfoTab.CONTEXT,
                    onClick = { onSelectInfoTab(InfoTab.CONTEXT) },
                    colorScheme = colorScheme,
                )
                ToolbarToggleButton(
                    icon = Icons.Outlined.Folder,
                    label = "Changes",
                    isActive = selectedInfoTab == InfoTab.CHANGES,
                    onClick = { onSelectInfoTab(InfoTab.CHANGES) },
                    colorScheme = colorScheme,
                )

                ToolbarDivider(colorScheme)

                // Agent & debug
                ToolbarToggleButton(
                    icon = Icons.Outlined.Notifications,
                    label = "Agent",
                    isActive = showAgentActivity,
                    onClick = onToggleAgentActivity,
                    colorScheme = colorScheme,
                )
                ToolbarToggleButton(
                    icon = Icons.Outlined.BugReport,
                    label = "Debug",
                    isActive = state.debugMode,
                    onClick = { engine.toggleDebugMode() },
                    colorScheme = colorScheme,
                )
                ToolbarChip(
                    icon = Icons.Outlined.AutoAwesome,
                    label = "Suggestions",
                    isActive = showSuggestions,
                    onClick = onToggleSuggestions,
                    colorScheme = colorScheme,
                )

                ToolbarDivider(colorScheme)

                // Settings & overflow
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(16.dp),
                    )
                }

                OverflowMenu(
                    expanded = showOverflow,
                    onToggle = { showOverflow = !showOverflow },
                    colorScheme = colorScheme,
                    onAction = { action ->
                        showOverflow = false
                        when (action) {
                            OverflowAction.SKILLS -> onOpenPanel(ToolPanel.SKILLS)
                            OverflowAction.AGENTS -> onOpenPanel(ToolPanel.AGENTS)
                            OverflowAction.RULES -> onOpenPanel(ToolPanel.INSTRUCTIONS)
                            OverflowAction.PLUGINS -> onOpenPanel(ToolPanel.PLUGINS)
                            OverflowAction.PERMS -> onOpenPanel(ToolPanel.PERMISSIONS)
                            OverflowAction.CLEAR -> onShowClearDialog()
                            OverflowAction.EXPORT -> onShowExportDialog()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelBadge(
    engine: VibeCodingEngine,
    colorScheme: ColorScheme,
) {
    val settings by engine.settingsStore.settingsFlow.collectAsState()
    val modelName = remember(settings.chatModelId, settings.providers) {
        val model = settings.providers.flatMap { it.models }
            .firstOrNull { it.id == settings.chatModelId }
        model?.displayName?.ifEmpty { model.modelId } ?: "No model"
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colorScheme.primaryContainer.copy(alpha = 0.4f),
    ) {
        Text(
            text = modelName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 120.dp)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ToolbarDivider(colorScheme: ColorScheme) {
    Spacer(Modifier.width(2.dp))
    HorizontalDivider(
        modifier = Modifier.height(16.dp).width(1.dp),
        color = colorScheme.outlineVariant.copy(alpha = 0.3f),
    )
    Spacer(Modifier.width(2.dp))
}

@Composable
private fun OverflowMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    colorScheme: ColorScheme,
    onAction: (OverflowAction) -> Unit,
) {
    Box {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "More",
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onToggle,
        ) {
            DropdownMenuItem(
                text = { Text("Skills", style = MaterialTheme.typography.bodySmall) },
                onClick = { onAction(OverflowAction.SKILLS) },
                leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) },
            )
            DropdownMenuItem(
                text = { Text("Agents", style = MaterialTheme.typography.bodySmall) },
                onClick = { onAction(OverflowAction.AGENTS) },
                leadingIcon = { Icon(Icons.Outlined.Psychology, null, Modifier.size(16.dp)) },
            )
            DropdownMenuItem(
                text = { Text("Rules", style = MaterialTheme.typography.bodySmall) },
                onClick = { onAction(OverflowAction.RULES) },
                leadingIcon = { Icon(Icons.Outlined.Description, null, Modifier.size(16.dp)) },
            )
            DropdownMenuItem(
                text = { Text("Plugins", style = MaterialTheme.typography.bodySmall) },
                onClick = { onAction(OverflowAction.PLUGINS) },
                leadingIcon = { Icon(Icons.Outlined.Extension, null, Modifier.size(16.dp)) },
            )
            DropdownMenuItem(
                text = { Text("Permissions", style = MaterialTheme.typography.bodySmall) },
                onClick = { onAction(OverflowAction.PERMS) },
                leadingIcon = { Icon(Icons.Outlined.Security, null, Modifier.size(16.dp)) },
            )
            DropdownMenuItem(
                text = { Text("Export", style = MaterialTheme.typography.bodySmall) },
                onClick = { onAction(OverflowAction.EXPORT) },
                leadingIcon = { Icon(Icons.Outlined.FileDownload, null, Modifier.size(16.dp)) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Clear chat", color = colorScheme.error, style = MaterialTheme.typography.bodySmall) },
                onClick = { onAction(OverflowAction.CLEAR) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp), tint = colorScheme.error) },
            )
        }
    }
}

@Composable
internal fun ToolbarChip(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
    colorScheme: ColorScheme,
) {
    val bg = if (isActive) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
    val content = if (isActive) colorScheme.onPrimaryContainer else colorScheme.onSurface

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(14.dp), tint = content)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ToolbarToggleButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    colorScheme: ColorScheme,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(28.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(14.dp),
            tint = if (isActive) colorScheme.primary else colorScheme.onSurfaceVariant,
        )
    }
}
