package com.rk.ai

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rk.activities.main.BottomPanelMode
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.terminal.TerminalViewModel
import com.rk.terminal.changeTerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(30.dp)
                ) {
                    XedIcon(
                        com.rk.icons.Icon.DrawableRes(drawables.undo),
                        modifier = Modifier.size(18.dp),
                        tint = if (canUndo) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                FilledTonalIconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(30.dp)
                ) {
                    XedIcon(
                        com.rk.icons.Icon.DrawableRes(drawables.redo),
                        modifier = Modifier.size(18.dp),
                        tint = if (canRedo) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                FilledTonalIconButton(onClick = onRestartAgent, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Restart", modifier = Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant)
                }

                FilledTonalIconButton(onClick = onSyncFiles, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.Save, contentDescription = "Sync", modifier = Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant)
                }
            }
        }

        BottomPanelMode.TERMINAL -> {
            var showSessionMenu by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val ctx = LocalContext.current
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    FilledTonalIconButton(onClick = { showSessionMenu = true }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Menu, contentDescription = "Sessions", tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    val service = terminalViewModel.sessionBinder?.getService()
                    DropdownMenu(expanded = showSessionMenu, onDismissRequest = { showSessionMenu = false }) {
                        service?.sessionList?.forEach { sessionId ->
                            DropdownMenuItem(
                                text = { Text(sessionId, style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    showSessionMenu = false
                                    terminalViewModel.terminalView?.let { termView ->
                                        val activity = termView.context as? Activity
                                        if (activity != null) {
                                            scope.launch {
                                                changeTerminalSession(sessionId, terminalViewModel, activity)
                                            }
                                        }
                                    }
                                },
                                leadingIcon = if (sessionId == service.currentSession.value) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("New Session", style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                showSessionMenu = false
                                terminalViewModel.terminalView?.let { tv ->
                                    val activity = tv.context as? Activity ?: return@let
                                    val client = com.rk.terminal.TerminalBackEnd(terminalViewModel)
                                    val sessionBinder = terminalViewModel.sessionBinder ?: return@let
                                    scope.launch(Dispatchers.IO) {
                                        sessionBinder.createSession(
                                            "main #${service?.sessionList?.size?.plus(1) ?: 1}",
                                            client,
                                            activity,
                                        )
                                    }
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                
                FilledTonalIconButton(onClick = {
                    android.content.Intent(ctx, com.rk.activities.settings.SettingsActivity::class.java).apply {
                        putExtra("route", com.rk.activities.settings.SettingsRoutes.TerminalSettings.route)
                        ctx.startActivity(this)
                    }
                }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant)
                }
            }
        }

        BottomPanelMode.GIT -> {}
    }
}
