package com.rk.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.activities.main.BottomPanelMode
import com.rk.terminal.TerminalViewModel

data class ToolSheetTab(
    val mode: BottomPanelMode,
    val icon: ImageVector,
    val label: String,
)

val toolSheetTabs = listOf(
    ToolSheetTab(
        mode = BottomPanelMode.AI,
        icon = Icons.Outlined.Psychology,
        label = "AI Agent",
    ),
    ToolSheetTab(
        mode = BottomPanelMode.VIBE_CODING,
        icon = Icons.Outlined.FlashOn,
        label = "VibeCoding",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSheetTabBar(
    selectedMode: BottomPanelMode,
    onSelectTab: (BottomPanelMode) -> Unit,
    terminalViewModel: TerminalViewModel,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val compact = LocalConfiguration.current.screenWidthDp < 390

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .background(colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        toolSheetTabs.forEach { tab ->
            val isSelected = selectedMode == tab.mode

            val containerColor = if (isSelected) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainerLow
            val contentColor = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant

            Surface(
                onClick = { onSelectTab(tab.mode) },
                shape = RoundedCornerShape(8.dp),
                color = containerColor,
                tonalElevation = if (isSelected) 2.dp else 0.dp,
                border = if (isSelected) {
                    BorderStroke(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.3f))
                } else null,
                modifier = Modifier.height(34.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = if (compact) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (tab.mode == BottomPanelMode.TERMINAL) {
                        BadgedBox(
                            badge = {
                                val sessionCount = terminalViewModel.sessionBinder?.getService()?.sessionList?.size ?: 0
                                if (sessionCount > 1) {
                                    Badge(
                                        containerColor = colorScheme.primary,
                                        contentColor = colorScheme.onPrimary,
                                        modifier = Modifier.offset(x = 2.dp, y = (-2).dp)
                                    ) {
                                        Text(sessionCount.toString(), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        ) {
                            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = contentColor)
                        }
                    } else {
                        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = contentColor)
                    }

                    if (!compact || isSelected) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
