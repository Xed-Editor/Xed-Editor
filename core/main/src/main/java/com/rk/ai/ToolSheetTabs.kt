package com.rk.ai

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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

@OptIn(ExperimentalMaterial3Api::class)
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
            .background(colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        toolSheetTabs.forEach { tab ->
            val isSelected = selectedMode == tab.mode
            
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) colorScheme.surfaceContainerHigh else Color.Transparent,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "TabContainerColor"
            )
            
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "TabContentColor"
            )

            val borderStroke = if (isSelected) {
                BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.2f))
            } else null

            Surface(
                onClick = { onSelectTab(tab.mode) },
                shape = RoundedCornerShape(6.dp),
                color = containerColor,
                border = borderStroke,
                modifier = Modifier.height(30.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
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
                                        modifier = Modifier.offset(x = 4.dp, y = (-4).dp)
                                    ) {
                                        Text(sessionCount.toString(), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        ) {
                            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor)
                        }
                    } else {
                        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor)
                    }
                    
                    Spacer(Modifier.width(4.dp))
                    
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = contentColor
                    )
                }
            }
        }
    }
}
