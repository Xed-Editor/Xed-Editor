package com.rk.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rk.activities.main.BottomPanelMode
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.terminal.TerminalViewModel

@Composable
fun ToolSheetControls(
    mode: BottomPanelMode,
    onRestartAgent: () -> Unit,
    onSyncFiles: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean,
    onRedo: () -> Unit,
    terminalViewModel: TerminalViewModel,
) {
    val colorScheme = MaterialTheme.colorScheme

    when (mode) {
        BottomPanelMode.AI -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    XedIcon(
                        com.rk.icons.Icon.DrawableRes(drawables.undo),
                        modifier = Modifier.size(16.dp),
                        tint = if (canUndo) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                FilledTonalIconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    XedIcon(
                        com.rk.icons.Icon.DrawableRes(drawables.redo),
                        modifier = Modifier.size(16.dp),
                        tint = if (canRedo) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                FilledTonalIconButton(
                    onClick = onRestartAgent,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Restart",
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalIconButton(
                    onClick = onSyncFiles,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Outlined.Save,
                        contentDescription = "Sync",
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        BottomPanelMode.TERMINAL -> {
            val ctx = LocalContext.current

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = {
                        android.content.Intent(ctx, com.rk.activities.settings.SettingsActivity::class.java).apply {
                            putExtra("route", com.rk.activities.settings.SettingsRoutes.TerminalSettings.route)
                            ctx.startActivity(this)
                        }
                    },
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        BottomPanelMode.GIT -> {}
    }
}
