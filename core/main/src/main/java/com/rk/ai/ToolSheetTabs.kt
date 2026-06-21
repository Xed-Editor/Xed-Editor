package com.rk.ai

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
    ToolSheetTab(mode = BottomPanelMode.AI, icon = Icons.Outlined.Psychology, label = "AI Agent"),
    ToolSheetTab(mode = BottomPanelMode.VIBE_CODING, icon = Icons.Outlined.FlashOn, label = "VibeCoding"),
    ToolSheetTab(mode = BottomPanelMode.TERMINAL, icon = Icons.Outlined.Terminal, label = "Terminal"),
    ToolSheetTab(mode = BottomPanelMode.GIT, icon = Icons.Outlined.Code, label = "Git"),
)

@Composable
fun ToolSheetTabBar(
    selectedMode: BottomPanelMode,
    onSelectTab: (BottomPanelMode) -> Unit,
    terminalViewModel: TerminalViewModel,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        toolSheetTabs.forEach { tab ->
            val isSelected = selectedMode == tab.mode

            Surface(
                onClick = { onSelectTab(tab.mode) },
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) colorScheme.surfaceContainerHighest else colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
                modifier = Modifier.height(30.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (tab.mode == BottomPanelMode.TERMINAL) {
                        BadgedBox(
                            badge = {
                                val count = terminalViewModel.sessionBinder?.getService()?.sessionList?.size ?: 0
                                if (count > 1) {
                                    Badge(
                                        containerColor = colorScheme.primary,
                                        contentColor = colorScheme.onPrimary,
                                    ) { Text(count.toString(), style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        ) {
                            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
