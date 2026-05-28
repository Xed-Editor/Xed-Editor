package com.rk.ai

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rk.activities.main.BottomPanelMode
import com.rk.settings.Settings
import com.rk.terminal.TerminalViewModel
import com.rk.terminal.changeTerminalSession
import com.rk.terminal.virtualkeys.VirtualKeysConstants
import com.rk.terminal.virtualkeys.VirtualKeysInfo
import com.rk.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.virtualkeys.VirtualKeysView
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun UnifiedCommandBar(
    mode: BottomPanelMode,
    isAiRunning: Boolean,
    cwd: String,
    agent: AiProvider.AgentInfo?,
    transcript: String,
    showTranscript: Boolean,
    onToggleTranscript: () -> Unit,
    onClearTranscript: () -> Unit,
    onAction: (String) -> Unit,
    terminalViewModel: TerminalViewModel,
    hasSelection: Boolean,
    currentFile: String,
) {
    var showAgentMenu by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val availableAgents = AiProvider.sessionManager?.availableAgents() ?: emptyList()

    Column(modifier = Modifier.fillMaxWidth().background(colorScheme.surfaceContainerHighest)) {
        StatusBar(
            mode = mode,
            isRunning = isAiRunning,
            cwd = cwd,
            agent = agent,
            availableAgents = availableAgents,
            showAgentMenu = showAgentMenu,
            onToggleAgentMenu = { showAgentMenu = !showAgentMenu },
            onSelectAgent = { selectedAgent ->
                showAgentMenu = false
                val current = AiProvider.sessionManager?.currentAgent
                if (selectedAgent.name != current?.name) {
                    AiProvider.sessionManager?.switchAgent(selectedAgent.name)
                    onAction("/restart")
                }
            },
            transcript = transcript,
            showTranscript = showTranscript,
            onToggleTranscript = onToggleTranscript,
            onClearTranscript = onClearTranscript,
            terminalViewModel = terminalViewModel,
        )

        if (mode == BottomPanelMode.TERMINAL || mode == BottomPanelMode.AI) {
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
            AndroidView<VirtualKeysView>(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                factory = { ctx ->
                    VirtualKeysView(ctx, null).apply {
                        setButtonTextColor(colorScheme.onSurface.toArgb())
                        setBackgroundColor(colorScheme.surfaceContainerHighest.toArgb())
                        runCatching {
                            val info = VirtualKeysInfo(
                                Settings.terminal_extra_keys,
                                "",
                                VirtualKeysConstants.CONTROL_CHARS_ALIASES,
                            )
                            reload(info)
                        }
                        terminalViewModel.virtualKeysView = this
                    }
                },
                update = { keys ->
                    val session = if (mode == BottomPanelMode.AI) {
                        AiProvider.sessionManager?.sessionState?.value as? TerminalSession
                    } else {
                        terminalViewModel.terminalView?.mTermSession
                    }
                    keys.setVirtualKeysViewClient(session?.let { VirtualKeysListener(it) })
                    keys.setButtonTextColor(colorScheme.onSurface.toArgb())
                    keys.setBackgroundColor(colorScheme.surfaceContainerHighest.toArgb())
                },
            )
        }

        if (mode == BottomPanelMode.AI) {
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.15f), thickness = 0.5.dp)
            QuickActions(
                isRunning = isAiRunning,
                currentFile = currentFile,
                hasSelection = hasSelection,
                onAction = onAction,
            )
        } else if (mode == BottomPanelMode.TERMINAL) {
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.15f), thickness = 0.5.dp)
            TerminalQuickActions(
                terminalViewModel = terminalViewModel,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun TerminalQuickActions(
    terminalViewModel: TerminalViewModel,
    onAction: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionChip(
            icon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp)) },
            label = "Paste",
            onClick = {
                terminalViewModel.terminalView?.mTermSession?.let { session ->
                    val clip = com.blankj.utilcode.util.ClipboardUtils.getText().toString()
                    if (clip.isNotBlank()) {
                        session.write(clip)
                    }
                }
            },
            color = colorScheme.secondaryContainer,
        )

        ActionChip(
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(14.dp)) },
            label = "Clear",
            onClick = {
                terminalViewModel.terminalView?.mTermSession?.write("clear\n")
            },
            color = colorScheme.secondaryContainer,
        )

        ActionChip(
            icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
            label = "New Session",
            onClick = {
                terminalViewModel.terminalView?.let { tv ->
                    val activity = tv.context as? android.app.Activity ?: return@let
                    val client = com.rk.terminal.TerminalBackEnd(terminalViewModel)
                    val service = terminalViewModel.sessionBinder?.getService()
                    val sessionBinder = terminalViewModel.sessionBinder ?: return@let
                    
                    val activeTab = (tv.context as? com.rk.activities.main.MainActivity)?.viewModel?.currentTab as? com.rk.tabs.editor.EditorTab
                    val activeFile = activeTab?.file?.getAbsolutePath() ?: ""
                    val activeProject = activeTab?.projectRoot?.getAbsolutePath() ?: ""
                    
                    scope.launch(Dispatchers.IO) {
                        sessionBinder.createSession(
                            "main #${service?.sessionList?.size?.plus(1) ?: 1}",
                            client,
                            activity,
                            activeFile = activeFile,
                            activeProject = activeProject
                        )
                    }
                }
            },
            color = colorScheme.surfaceVariant,
        )

        ActionChip(
            icon = { Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(14.dp)) },
            label = "Kill Session",
            onClick = {
                terminalViewModel.terminalView?.mTermSession?.let { session ->
                    val binder = terminalViewModel.sessionBinder ?: return@let
                    val service = binder.getService() ?: return@let
                    val sessionId = service.sessionList.find { binder.getSession(it) == session }
                    if (sessionId != null) {
                        binder.terminateSession(sessionId)
                        val nextSession = service.sessionList.firstOrNull()
                        if (nextSession != null) {
                            val activity = terminalViewModel.terminalView?.context as? android.app.Activity
                            if (activity != null) {
                                scope.launch {
                                    changeTerminalSession(nextSession, terminalViewModel, activity)
                                }
                            }
                        }
                    }
                }
            },
            color = colorScheme.errorContainer,
            labelColor = colorScheme.error,
        )
    }
}

@Composable
private fun StatusBar(
    mode: BottomPanelMode = BottomPanelMode.AI,
    isRunning: Boolean = false,
    cwd: String = "",
    agent: AiProvider.AgentInfo? = AiProvider.sessionManager?.currentAgent,
    availableAgents: List<AiProvider.AgentInfo> = emptyList(),
    showAgentMenu: Boolean = false,
    onToggleAgentMenu: () -> Unit = {},
    onSelectAgent: (AiProvider.AgentInfo) -> Unit = {},
    transcript: String = "",
    showTranscript: Boolean = false,
    onToggleTranscript: () -> Unit = {},
    onClearTranscript: () -> Unit = {},
    terminalViewModel: TerminalViewModel? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val statusColor = if (isRunning) Color(0xFF4CAF50) else Color(0xFFEF5350)
    val bridgeClients = AiProvider.ideBridge?.connectedClients() ?: 0
    val bridgeOnline = AiProvider.ideBridge?.isRunning() == true

    val infiniteTransition = rememberInfiniteTransition(label = "PulseState")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Surface(
                    onClick = { if (mode == BottomPanelMode.AI) onToggleAgentMenu() },
                    enabled = mode == BottomPanelMode.AI,
                    shape = RoundedCornerShape(14.dp),
                    color = if (mode == BottomPanelMode.AI)
                        colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else
                        colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(12.dp)) {
                            if (isRunning) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(statusColor.copy(alpha = pulseAlpha * 0.4f))
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (mode == BottomPanelMode.AI) (agent?.displayName ?: "AI") else "Terminal",
                            color = if (mode == BottomPanelMode.AI) colorScheme.onPrimaryContainer else colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        if (mode == BottomPanelMode.AI) {
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Switch",
                                modifier = Modifier.size(14.dp),
                                tint = colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                            )
                        }
                    }
                }

                if (mode == BottomPanelMode.AI) {
                    DropdownMenu(
                        expanded = showAgentMenu,
                        onDismissRequest = { onToggleAgentMenu() },
                    ) {
                        availableAgents.forEachIndexed { index, a ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (a.cliBinaryName == "gemini") Icons.Outlined.Psychology else Icons.Outlined.AutoFixHigh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (a == agent) colorScheme.primary else colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(a.displayName, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "Switch to ${a.displayName.split("/").last()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            )
                                        }
                                        if (a == agent) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                                onClick = { onSelectAgent(a) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                onClick = {
                    com.blankj.utilcode.util.ClipboardUtils.copyText("Path", cwd)
                    com.rk.utils.toast("Path copied to clipboard")
                },
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Code,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = cwd.split("/").lastOrNull()?.takeIf { it.isNotBlank() } ?: "/",
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            if (bridgeOnline && mode == BottomPanelMode.AI) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (bridgeClients > 0) Color(0xFF4CAF50)
                                    else Color(0xFFFFC107)
                                )
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
            }

            Spacer(Modifier.weight(1f))

            if (mode == BottomPanelMode.AI && transcript.isNotBlank()) {
                FilledTonalIconButton(onClick = onToggleTranscript, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (showTranscript) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (showTranscript) "Hide transcript" else "Show transcript",
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalIconButton(onClick = onClearTranscript, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Clear transcript",
                        modifier = Modifier.size(14.dp),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showTranscript && transcript.isNotBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                val scrollState = rememberScrollState()
                LaunchedEffect(transcript) {
                    if (showTranscript) scrollState.animateScrollTo(scrollState.maxValue)
                }
                Text(
                    text = transcript,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .verticalScroll(scrollState),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun QuickActions(
    isRunning: Boolean,
    currentFile: String,
    hasSelection: Boolean,
    onAction: (String) -> Unit,
) {
    fun prompt(text: String) = buildString {
        append(text)
        if (currentFile.isNotBlank()) append(" in $currentFile")
        if (hasSelection) append(" (selected code provided as context)")
    }

    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isRunning) {
            Button(
                onClick = { onAction("/restart") },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start Agent", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            ActionChip(
                icon = { Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Explain",
                onClick = { onAction(prompt("Explain the code")) },
                color = colorScheme.secondaryContainer,
            )
            ActionChip(
                icon = { Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Bugs",
                onClick = { onAction(prompt("Find bugs and issues")) },
                color = colorScheme.secondaryContainer,
            )
            ActionChip(
                icon = { Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Refactor",
                onClick = { onAction(prompt("Suggest improvements")) },
                color = colorScheme.secondaryContainer,
            )
            ActionChip(
                icon = { Icon(Icons.Outlined.Science, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Tests",
                onClick = { onAction(prompt("Add unit tests")) },
                color = colorScheme.secondaryContainer,
            )
            ActionChip(
                icon = { Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Docs",
                onClick = { onAction(prompt("Write documentation")) },
                color = colorScheme.secondaryContainer,
            )

            Spacer(Modifier.width(4.dp))
            VerticalDivider(modifier = Modifier.height(18.dp), color = colorScheme.outlineVariant)
            Spacer(Modifier.width(4.dp))

            ActionChip(
                icon = { Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Export",
                onClick = { onAction("/export") },
                color = colorScheme.surfaceVariant,
            )
            ActionChip(
                icon = { Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Stop",
                onClick = { onAction("/stop") },
                color = colorScheme.errorContainer,
                labelColor = colorScheme.error,
            )
        }
    }
}

@Composable
internal fun ActionChip(
    icon: (@Composable () -> Unit)? = null,
    label: String,
    onClick: () -> Unit,
    color: Color,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.15f)),
        modifier = Modifier.height(32.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon?.invoke()
            if (icon != null) Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = labelColor
            )
        }
    }
}
