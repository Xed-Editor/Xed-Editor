package com.rk.ai.nativeagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class QuickStartAction(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val prompt: String,
)

private val quickStartActions = listOf(
    QuickStartAction(Icons.Outlined.BugReport, "Fix Bugs", "Find & fix issues", "Find and fix issues in the current code"),
    QuickStartAction(Icons.Outlined.Science, "Add Tests", "Write test cases", "Write tests for the codebase"),
    QuickStartAction(Icons.Outlined.Refresh, "Refactor", "Improve quality", "Refactor the codebase for better quality"),
    QuickStartAction(Icons.Outlined.Description, "Document", "Generate docs", "Add documentation to the code"),
    QuickStartAction(Icons.Outlined.RateReview, "Review", "Code review", "Review recent changes for issues"),
    QuickStartAction(Icons.Outlined.AccountTree, "Plan", "Create a plan", "Create a step-by-step plan for a task"),
)

@Composable
internal fun VibeCodingEmptyState(
    colorScheme: ColorScheme,
    workspacePath: String = "",
    onQuickAction: ((String) -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            // Hero icon
            Icon(
                Icons.Outlined.AutoFixHigh,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = colorScheme.primary.copy(alpha = 0.4f),
            )

            // Title
            Text(
                text = "VibeCoding Ready",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )

            // Subtitle
            Text(
                text = "Ask a question or pick a quick action below",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            // Workspace badge
            if (workspacePath.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Text(
                            text = workspacePath,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Quick start actions
            if (onQuickAction != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "QUICK ACTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(4.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    items(quickStartActions) { action ->
                        QuickStartCard(
                            icon = action.icon,
                            title = action.title,
                            description = action.description,
                            colorScheme = colorScheme,
                            onClick = { onQuickAction(action.prompt) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStartCard(
    icon: ImageVector,
    title: String,
    description: String,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        modifier = Modifier.width(120.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.primary,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 2,
                lineHeight = 14.sp,
            )
        }
    }
}
