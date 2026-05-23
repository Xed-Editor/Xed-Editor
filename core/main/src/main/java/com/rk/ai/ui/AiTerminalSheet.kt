package com.rk.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.activities.main.MainViewModel
import com.rk.ai.agents.AiAgent
import com.rk.ai.context.ContextBuilder
import com.rk.ai.runtime.AgentRuntime
import com.rk.ai.runtime.AgentSessionConfig
import com.rk.ai.runtime.SessionPhase
import com.rk.ai.runtime.StreamEvent
import com.rk.ai.session.AiSessionManager
import com.rk.tabs.editor.EditorTab
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTerminalSheet(
    viewModel: MainViewModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val runtime = remember { AgentRuntime.get() ?: AgentRuntime.initialize() }
    val sessionId by runtime.currentSessionId.collectAsState()
    val sessionHandle = sessionId?.let { runtime.getSession(it) }
    val sessionState by sessionHandle?.state?.collectAsState() ?: remember { mutableStateOf(null) }

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val events = sessionState?.events ?: emptyList()
    val phase = sessionState?.phase ?: SessionPhase.IDLE
    val accumulatedText = sessionState?.accumulatedText ?: ""
    val error = sessionState?.error

    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            listState.animateScrollToItem(events.size - 1)
        }
    }

    val currentTab = viewModel.currentTab as? EditorTab
    val editor = currentTab?.editorState?.editor?.get()
    val selectedText = editor?.getSelectedText().orEmpty()
    val currentFile = currentTab?.file?.getName() ?: ""
    val fileContext = ContextBuilder.buildFileContext(currentTab)

    val agentList = remember { AiSessionManager.availableAgents() }
    var showAgentMenu by remember { mutableStateOf(false) }
    val currentAgent = remember { AiSessionManager.currentAgent }

    val isStreaming = phase == SessionPhase.STREAMING || phase == SessionPhase.RUNNING

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                isRunning = isStreaming,
                agentName = currentAgent.displayName,
                model = sessionState?.config?.model?.ifEmpty { currentAgent.defaultModel } ?: currentAgent.defaultModel,
                showAgentMenu = showAgentMenu,
                agentList = agentList,
                currentAgent = currentAgent,
                onToggleAgentMenu = { showAgentMenu = !showAgentMenu },
                onSelectAgent = { agent ->
                    showAgentMenu = false
                    if (agent != currentAgent) {
                        AiSessionManager.switchAgent(agent.name)
                    }
                },
                onStop = { sessionHandle?.cancel() },
                onClear = {
                    viewModel.agentTranscript = ""
                },
                onDismiss = onDismissRequest,
                onNewSession = {
                    runtime.destroyAll()
                    val config = AgentSessionConfig(
                        agent = currentAgent,
                        workingDir = "/",
                    )
                    runtime.createSession(config)
                },
            )

            if (events.isEmpty() && !isStreaming) {
                EmptyState(
                    selectedText = selectedText,
                    currentFile = currentFile,
                    onQuickAction = { prompt ->
                        val fileCtx = ContextBuilder.buildFileContext(currentTab)
                        val ctx = com.rk.ai.context.IdeContext(
                            currentFile = fileCtx.filePath,
                            selectedText = fileCtx.selectedText,
                            cursorLine = fileCtx.cursorLine,
                            cursorColumn = fileCtx.cursorColumn,
                            projectRoot = currentTab?.projectRoot?.getAbsolutePath() ?: "",
                            language = fileCtx.language,
                            fileExtension = fileCtx.extension,
                            lineCount = fileCtx.lineCount,
                        )
                        val fullPrompt = ContextBuilder.buildFullPrompt(prompt, ctx)
                        sessionHandle?.execute(fullPrompt)
                    },
                )
            } else {
                EventList(
                    events = events,
                    phase = phase,
                    listState = listState,
                    modifier = Modifier.weight(1f),
                )
            }

            if (error != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            InputBar(
                value = input,
                onValueChange = { input = it },
                enabled = !isStreaming,
                onSend = {
                    if (input.isBlank()) return@InputBar
                    val prompt = input.trim()
                    input = ""

                    val ctx = com.rk.ai.context.IdeContext(
                        currentFile = fileContext.filePath,
                        selectedText = fileContext.selectedText,
                        cursorLine = fileContext.cursorLine,
                        cursorColumn = fileContext.cursorColumn,
                        projectRoot = currentTab?.projectRoot?.getAbsolutePath() ?: "",
                        language = fileContext.language,
                        fileExtension = fileContext.extension,
                        lineCount = fileContext.lineCount,
                    )
                    val fullPrompt = ContextBuilder.buildFullPrompt(prompt, ctx)

                    if (sessionHandle == null || !sessionHandle.isActive) {
                        val config = AgentSessionConfig(
                            agent = currentAgent,
                            workingDir = "/",
                        )
                        val sid = runtime.createSession(config)
                        runtime.getSession(sid)?.execute(fullPrompt)
                    } else {
                        sessionHandle.execute(fullPrompt)
                    }
                },
            )
        }
    }
}

@Composable
private fun Header(
    isRunning: Boolean,
    agentName: String,
    model: String,
    showAgentMenu: Boolean,
    agentList: List<AiAgent>,
    currentAgent: AiAgent,
    onToggleAgentMenu: () -> Unit,
    onSelectAgent: (AiAgent) -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    onNewSession: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (isRunning) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                        RoundedCornerShape(4.dp),
                    )
            )
            Spacer(Modifier.width(8.dp))

            Box {
                TextButton(onClick = onToggleAgentMenu) {
                    Text(
                        agentName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Icon(
                        if (showAgentMenu) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(expanded = showAgentMenu, onDismissRequest = onToggleAgentMenu) {
                    agentList.forEach { agent ->
                        DropdownMenuItem(
                            text = { Text(agent.displayName) },
                            onClick = { onSelectAgent(agent) },
                            leadingIcon = if (agent == currentAgent) {
                                { Text("  ", style = MaterialTheme.typography.bodySmall) }
                            } else null,
                        )
                    }
                }
            }

            if (model.isNotBlank()) {
                Text(
                    text = model,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.tertiary,
                    modifier = Modifier
                        .background(colorScheme.tertiaryContainer.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            if (isRunning) {
                TextButton(onClick = onStop, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Outlined.Stop, contentDescription = "Stop", tint = colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop", style = MaterialTheme.typography.labelSmall, color = colorScheme.error)
                }
            }

            TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = "Clear", modifier = Modifier.size(18.dp))
            }

            TextButton(onClick = onNewSession, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = "New", modifier = Modifier.size(18.dp))
            }

            TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(
    selectedText: String,
    currentFile: String,
    onQuickAction: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            text = "AI Terminal",
            style = MaterialTheme.typography.headlineSmall,
            color = colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ask about your code, run commands, or use tools",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )

        if (currentFile.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "File: $currentFile",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickActionChip("Explain this code") { onQuickAction("Explain this code") }
            QuickActionChip("Find bugs") { onQuickAction("Find bugs and issues in this code") }
            QuickActionChip("Add tests") { onQuickAction("Add unit tests for this code") }
            QuickActionChip("Refactor") { onQuickAction("Suggest improvements and refactor") }
        }
    }
}

@Composable
private fun QuickActionChip(text: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
private fun EventList(
    events: List<StreamEvent>,
    phase: SessionPhase,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(events, key = { it.hashCode() }) { event ->
            when (event) {
                is StreamEvent.Token -> {
                    Text(
                        text = event.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        ),
                        color = colorScheme.onSurface,
                    )
                }
                is StreamEvent.ToolCall -> {
                    ToolCallBanner(event.name, event.args)
                }
                is StreamEvent.ToolResult -> {
                    ToolResultBanner(event.callId, event.output, event.error)
                }
                is StreamEvent.Error -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = event.message,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = colorScheme.onErrorContainer,
                        )
                    }
                }
                is StreamEvent.Done -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (event.finishReason == "stop") "  Done" else "  Finished: ${event.finishReason}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                is StreamEvent.StreamStart -> {}
                is StreamEvent.Status -> {
                    Text(
                        text = event.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }

            }
        }

        if (phase == SessionPhase.STREAMING || phase == SessionPhase.RUNNING) {
            item {
                PulsingCursor()
            }
        }
    }
}

@Composable
private fun ToolCallBanner(name: String, args: String) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colorScheme.secondaryContainer.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Tool: $name",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ToolResultBanner(callId: String, output: String, error: String?) {
    val colorScheme = MaterialTheme.colorScheme
    val isError = error != null

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isError) colorScheme.errorContainer else colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = if (isError) "Tool Error: $error" else "Tool Result",
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) colorScheme.onErrorContainer else colorScheme.onTertiaryContainer,
            )
            if (output.isNotBlank()) {
                Text(
                    text = output.take(300),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 5,
                )
            }
        }
    }
}

@Composable
private fun PulsingCursor() {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp, 16.dp)
                .background(colorScheme.primary, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask or type a command...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (enabled) onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && enabled,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "Send",
                )
            }
        }
    }
}
