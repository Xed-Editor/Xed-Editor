package com.rk.ai

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.activities.main.BottomPanelMode
import com.rk.terminal.TerminalViewModel

data class ToolSheetTab(
    val mode: BottomPanelMode,
    val icon: ImageVector,
    val label: String,
    val badge: (@Composable () -> Unit)? = null,
)

val toolSheetTabs = listOf(
    ToolSheetTab(
        mode = BottomPanelMode.AI,
        icon = Icons.Outlined.Psychology,
        label = "AI Agent",
    ),
    ToolSheetTab(
        mode = BottomPanelMode.TERMINAL,
        icon = Icons.Outlined.Terminal,
        label = "Terminal",
    ),
    ToolSheetTab(
        mode = BottomPanelMode.GIT,
        icon = Icons.Outlined.Code,
        label = "Git",
    ),
)

@Composable
fun ToolSheetTabBar(
    selectedMode: BottomPanelMode,
    onSelectTab: (BottomPanelMode) -> Unit,
    terminalViewModel: TerminalViewModel,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    TabRow(
        selectedTabIndex = toolSheetTabs.indexOfFirst { it.mode == selectedMode }.coerceAtLeast(0),
        containerColor = Color.Transparent,
        divider = {},
        indicator = { tabPositions ->
            val idx = toolSheetTabs.indexOfFirst { it.mode == selectedMode }.coerceAtLeast(0)
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(tabPositions[idx]),
                color = colorScheme.primary,
                height = 3.dp
            )
        },
        modifier = modifier.height(48.dp),
    ) {
        toolSheetTabs.forEach { tab ->
            Tab(
                selected = selectedMode == tab.mode,
                onClick = { onSelectTab(tab.mode) },
                enabled = true,
                icon = {
                    if (tab.badge != null) {
                        BadgedBox(badge = tab.badge) {
                            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    } else if (tab.mode == BottomPanelMode.TERMINAL) {
                        BadgedBox(
                            badge = {
                                val sessionCount = terminalViewModel.sessionBinder?.getService()?.sessionList?.size ?: 0
                                if (sessionCount > 1) {
                                    Badge(
                                        containerColor = colorScheme.primary,
                                        contentColor = colorScheme.onPrimary,
                                    ) {
                                        Text(sessionCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                },
                text = {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedMode == tab.mode) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                selectedContentColor = if (enabled) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                unselectedContentColor = if (enabled) colorScheme.onSurfaceVariant else colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        }
    }
}
