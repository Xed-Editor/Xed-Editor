@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.agent.events.SessionTodoStatus
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.nativeagent.ui.components.*
import com.rk.ai.nativeagent.ui.panels.*
import com.rk.ai.persistence.settings.getCurrentAssistant
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

enum class ToolPanel {
    NONE, COMMANDS, SKILLS, AGENTS, PERMISSIONS, INSTRUCTIONS, PLUGINS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeCodingPanel(
    engine: VibeCodingEngine,
    modifier: Modifier = Modifier,
) {
    val state by engine.state.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    var showSettings by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showFiles by remember { mutableStateOf(false) }
    var showAgentPanel by remember { mutableStateOf(false) }
    var showSuggestionPanel by remember { mutableStateOf(false) }
    var showExecutionGraph by remember { mutableStateOf(false) }
    var showToolActivity by remember { mutableStateOf(false) }
    var showContext by remember { mutableStateOf(false) }
    var showFileChanges by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf(ToolPanel.NONE) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val workspacePath = remember {
        try {
            engine.ideService.getPrimaryWorkspacePath()
        } catch (_: Exception) { "/" }
    }

    val dockProgress by animateDpAsState(
        targetValue = if (state.dockOpen) 0.dp else (-200).dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "dockAnimation",
    )

    val hasTodos = state.todos.isNotEmpty() && state.dockOpen
    val todoProgress by animateDpAsState(
        targetValue = if (hasTodos) 0.dp else (-120).dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "todoAnimation",
    )

    // Bottom sheet panel content
    if (activePanel != ToolPanel.NONE) {
        ModalBottomSheet(
            onDismissRequest = { activePanel = ToolPanel.NONE },
            sheetState = sheetState,
        ) {
            when (activePanel) {
                ToolPanel.COMMANDS -> {
                    val builtinCommands = remember {
                        listOf(
                            PaletteCommand("init", "Init", "Initialize project instructions (AGENTS.md)", "Initialize project with AGENTS.md based on codebase analysis", "Project"),
                            PaletteCommand("review", "Review", "Review recent code changes", "Review all uncommitted changes for bugs and quality", "Code"),
                            PaletteCommand("test", "Test", "Run tests and analyze results", "Run the test suite and report failures with fix suggestions", "Code"),
                            PaletteCommand("commit", "Commit", "Stage and commit changes", "Stage all changes and create a descriptive commit", "Git"),
                            PaletteCommand("push", "Push", "Push commits to remote", "Push the current branch to origin", "Git"),
                            PaletteCommand("changelog", "Changelog", "Generate changelog from recent commits", "Generate a changelog file from git history", "Project"),
                            PaletteCommand("spellcheck", "Spell Check", "Check spelling in markdown files", "Run spell check on all changed markdown files", "Code"),
                            PaletteCommand("translate", "Translate", "Translate documentation", "Translate changed documentation to configured languages", "Project"),
                            PaletteCommand("summarize", "Summarize", "Summarize current conversation", "Create a summary of the conversation context for reference", "General"),
                            PaletteCommand("compact", "Compact", "Compact conversation context", "Compact the conversation to free context window space", "General"),
                            PaletteCommand("learn", "Learn", "Extract learnings to AGENTS.md", "Analyze session and extract non-obvious learnings to AGENTS.md files", "Project"),
                            PaletteCommand("rmslop", "Remove Slop", "Remove AI-generated code slop", "Clean up unnecessary comments, defensive checks, and style inconsistencies", "Code"),
                            PaletteCommand("issues", "Issues", "Find matching GitHub issues", "Search GitHub issues matching the current context", "Git"),
                            PaletteCommand("feature-dev", "Feature Dev", "Guided feature development", "Systematic 7-phase feature dev: discover, explore, design, implement, review", "Feature"),
                        )
                    }
                    val fileCommands = remember {
                        engine.getCommandCatalog()
                            .filter { it.id.startsWith("file:") }
                            .map { cmd ->
                                PaletteCommand(
                                    id = cmd.id,
                                    name = cmd.title,
                                    description = cmd.description,
                                    prompt = cmd.prompt,
                                    category = cmd.category,
                                )
                            }
                    }
                    CommandPaletteSheet(
                        builtinCommands = builtinCommands,
                        fileCommands = fileCommands,
                        onDismiss = { activePanel = ToolPanel.NONE },
                        onExecuteCommand = { command ->
                            engine.sendMessage(command.prompt)
                            activePanel = ToolPanel.NONE
                        },
                        onRefreshCommands = {
                            engine.refreshCommands()
                        },
                        modifier = Modifier.fillMaxHeight(0.85f),
                    )
                }
                ToolPanel.SKILLS -> {
                    val settings by engine.settingsStore.settingsFlow.collectAsState()
                    val currentAssistant = settings.getCurrentAssistant()
                    SkillBrowserPanel(
                        skillsDir = "$workspacePath/.xed/skills",
                        enabledSkills = currentAssistant.enabledSkills,
                        onToggleSkill = { skillName, enabled ->
                            scope.launch {
                                engine.settingsStore.update { s ->
                                    s.copy(
                                        assistants = s.assistants.map { a ->
                                            if (a.id == currentAssistant.id) {
                                                val updatedSkills = if (enabled) {
                                                    a.enabledSkills + skillName
                                                } else {
                                                    a.enabledSkills - skillName
                                                }
                                                a.copy(enabledSkills = updatedSkills)
                                            } else a
                                        }
                                    )
                                }
                            }
                        },
                        onEditSkill = { engine.openFileInEditor(it) },
                        onDismiss = { activePanel = ToolPanel.NONE },
                        modifier = Modifier.fillMaxHeight(0.85f),
                    )
                }
                ToolPanel.AGENTS -> AgentConfigPanel(
                    settingsStore = engine.settingsStore,
                    onDismiss = { activePanel = ToolPanel.NONE },
                    modifier = Modifier.fillMaxHeight(0.85f),
                )
                ToolPanel.PERMISSIONS -> PermissionEditorPanel(
                    engine = engine,
                    onDismiss = { activePanel = ToolPanel.NONE },
                    modifier = Modifier.fillMaxHeight(0.85f),
                )
                ToolPanel.INSTRUCTIONS -> InstructionsEditorPanel(
                    workspacePath = workspacePath,
                    onDismiss = { activePanel = ToolPanel.NONE },
                    modifier = Modifier.fillMaxHeight(0.85f),
                )
                ToolPanel.PLUGINS -> PluginManagerPanel(
                    onDismiss = { activePanel = ToolPanel.NONE },
                    modifier = Modifier.fillMaxHeight(0.85f),
                )
                ToolPanel.NONE -> {}
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // File tree sidebar
            AnimatedVisibility(
                visible = showFiles,
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it },
            ) {
                VibeCodingFileTreeSidebar(
                    ideService = engine.ideService,
                    workspacePath = workspacePath,
                    onOpenFile = { path -> engine.openFileInEditor(path) },
                    onDismiss = { showFiles = false },
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight(),
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Top toolbar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colorScheme.surfaceContainerLow,
                    tonalElevation = 0.5.dp,
                ) {
                    Column {
                        // Main toolbar row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val settings by engine.settingsStore.settingsFlow.collectAsState()
                                val modelName = remember(settings.chatModelId, settings.providers) {
                                    val model = settings.providers.flatMap { it.models }
                                        .firstOrNull { it.id == settings.chatModelId }
                                    model?.displayName?.ifEmpty { model.modelId } ?: "No model"
                                }

                                Text(
                                    text = modelName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.primary,
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ToolbarButton(
                                    icon = Icons.Outlined.Terminal,
                                    label = "Commands",
                                    onClick = { activePanel = ToolPanel.COMMANDS },
                                )
                                ToolbarButton(
                                    icon = Icons.Outlined.AutoAwesome,
                                    label = "Skills",
                                    onClick = { activePanel = ToolPanel.SKILLS },
                                )
                                ToolbarButton(
                                    icon = Icons.Outlined.Psychology,
                                    label = "Agents",
                                    onClick = { activePanel = ToolPanel.AGENTS },
                                )
                                ToolbarButton(
                                    icon = Icons.Outlined.Code,
                                    label = "Files",
                                    onClick = { showFiles = !showFiles },
                                )
                                ToolbarButton(
                                    icon = Icons.Outlined.List,
                                    label = "History",
                                    onClick = { showHistory = !showHistory },
                                )
                                PanelToggleButton(
                                    icon = Icons.Outlined.Checklist,
                                    label = "Plan",
                                    isActive = showExecutionGraph,
                                    onClick = { showExecutionGraph = !showExecutionGraph },
                                )
                                PanelToggleButton(
                                    icon = Icons.Outlined.Build,
                                    label = "Tools",
                                    isActive = showToolActivity,
                                    onClick = { showToolActivity = !showToolActivity },
                                )
                                PanelToggleButton(
                                    icon = Icons.Outlined.Info,
                                    label = "Ctx",
                                    isActive = showContext,
                                    onClick = { showContext = !showContext },
                                )
                                PanelToggleButton(
                                    icon = Icons.Outlined.Folder,
                                    label = "Changes",
                                    isActive = showFileChanges,
                                    onClick = { showFileChanges = !showFileChanges },
                                )
                                PanelToggleButton(
                                    icon = Icons.Outlined.Notifications,
                                    label = "Agent",
                                    isActive = showAgentPanel,
                                    onClick = { showAgentPanel = !showAgentPanel },
                                )
                                ToolbarButton(
                                    icon = Icons.Outlined.Description,
                                    label = "Rules",
                                    onClick = { activePanel = ToolPanel.INSTRUCTIONS },
                                )
                                ToolbarButton(
                                    icon = Icons.Outlined.Extension,
                                    label = "Plugins",
                                    onClick = { activePanel = ToolPanel.PLUGINS },
                                )
                                ToolbarButton(
                                    icon = Icons.Outlined.Security,
                                    label = "Perms",
                                    onClick = { activePanel = ToolPanel.PERMISSIONS },
                                )
                                ToolbarButton(
                                    icon = Icons.Outlined.Settings,
                                    label = "Settings",
                                    onClick = { showSettings = true },
                                )
                                ToolbarButton(
                                    icon = Icons.Outlined.Delete,
                                    label = "Clear",
                                    onClick = { showClearDialog = true },
                                    enabled = state.messages.isNotEmpty(),
                                )
                            }
                        }

                        // Session tabs row
                        if (state.sessionTree.isNotEmpty()) {
                            SessionTabsRow(
                                sessionTree = state.sessionTree,
                                activeSessionId = state.activeSessionId,
                                isProcessing = state.isProcessing,
                                onSwitchSession = { engine.switchToSession(it) },
                                onNewBranch = {
                                    val parent = state.activeSessionId ?: return@SessionTabsRow
                                    engine.createBranchSession(parent)
                                },
                            )
                        }
                    }
                }

                // Security alerts
                if (state.hasSecurityAlerts) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                    ) {
                        items(state.securityAlerts.takeLast(3)) { alert ->
                            SecurityAlertBanner(
                                alert = alert,
                                onDismiss = { engine.clearSecurityAlerts() },
                            )
                        }
                    }
                }

                // Todo dock (animated)
                AnimatedVisibility(
                    visible = hasTodos,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.surfaceContainerHigh,
                        tonalElevation = 2.dp,
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "Tasks (${state.completedTodos}/${state.todos.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                TextButton(
                                    onClick = { engine.setSessionTodos(state.activeSessionId ?: return@TextButton, emptyList()) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            state.todos.take(3).forEach { todo ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                ) {
                                    val icon = when (todo.status) {
                                        SessionTodoStatus.COMPLETED -> Icons.Outlined.CheckCircle
                                        SessionTodoStatus.IN_PROGRESS -> Icons.Outlined.PlayCircle
                                        SessionTodoStatus.CANCELLED -> Icons.Outlined.Cancel
                                        SessionTodoStatus.PENDING -> Icons.Outlined.RadioButtonUnchecked
                                    }
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = when (todo.status) {
                                            SessionTodoStatus.COMPLETED -> colorScheme.primary
                                            SessionTodoStatus.IN_PROGRESS -> colorScheme.tertiary
                                            SessionTodoStatus.CANCELLED -> colorScheme.error
                                            SessionTodoStatus.PENDING -> colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = todo.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                // Agent activity panel (collapsible)
                AnimatedVisibility(
                    visible = showAgentPanel && state.agentActivities.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.agentActivities.takeLast(5)) { activity ->
                            AgentActivityCard(
                                activity = activity,
                            )
                        }
                    }
                }

                // Agent info panels (responsive row)
                val hasAnyPanel = showExecutionGraph || showToolActivity || showContext || showFileChanges
                if (hasAnyPanel) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.surfaceContainerLowest,
                    ) {
                        var selectedPanelIndex by remember { mutableStateOf(0) }
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                val panelTabs = listOf(
                                    showExecutionGraph to "Plan",
                                    showToolActivity to "Tools",
                                    showContext to "Context",
                                    showFileChanges to "Files",
                                )
                                panelTabs.forEachIndexed { index, (visible, label) ->
                                    if (visible) {
                                        FilterChip(
                                            selected = selectedPanelIndex == index,
                                            onClick = { selectedPanelIndex = index },
                                            label = { Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp) },
                                            modifier = Modifier.height(24.dp),
                                        )
                                    }
                                }
                            }
                            when (selectedPanelIndex) {
                                0 -> ExecutionGraphPanel(taskTree = state.taskTree, modifier = Modifier.weight(1f))
                                1 -> ToolActivityPanel(toolExecutions = state.toolExecutions, modifier = Modifier.weight(1f))
                                2 -> {
                                    val contextInfo = ContextInfo(
                                        currentGoal = engine.contextMemoryManager.conversation.getCurrentGoal(),
                                        modifiedFiles = state.modifiedFiles,
                                        projectIndexed = state.projectIndexed,
                                        toolCalls = state.toolExecutions.size,
                                    )
                                    ContextPanel(info = contextInfo, modifier = Modifier.weight(1f))
                                }
                                3 -> FileChangePanel(modifiedFiles = state.modifiedFiles, currentPhase = state.currentPhase, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Main message list
                VibeCodingMessageList(
                    messages = state.messages,
                    isProcessing = state.isProcessing,
                    onApproveTool = { toolCallId -> engine.approveTool(toolCallId) },
                    onDenyTool = { toolCallId, reason -> engine.denyTool(toolCallId, reason) },
                    onAnswerTool = { toolCallId, answer -> engine.answerTool(toolCallId, answer) },
                    modifier = Modifier.weight(1f),
                )

                // Error banner
                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colorScheme.errorContainer.copy(alpha = 0.8f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { engine.clearError() },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Dismiss",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                // Status bar
                VibeCodingStatusBar(
                    state = state,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                // Input area
                VibeCodingInput(
                    isProcessing = state.isProcessing,
                    onSend = { text, parts -> engine.sendMessage(text, parts) },
                    onStop = { engine.stopGeneration() },
                )
            }
        }

        // History sidebar
        AnimatedVisibility(
            visible = showHistory,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            VibeCodingConversationSidebar(
                conversationRepo = engine.generationHandler.conversationRepo,
                currentConversationId = state.currentConversationId,
                assistantId = engine.getCurrentAssistantId(),
                onSelectConversation = { conversation ->
                    engine.loadConversation(conversation)
                    showHistory = false
                },
                onDismiss = { showHistory = false },
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight(),
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Conversation") },
            text = { Text("This will permanently delete all messages in this conversation. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        engine.clearConversation()
                    },
                ) {
                    Text("Clear", color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showSettings) {
        VibeCodingSettingsSheet(
            engine = engine,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun SessionTabsRow(
    sessionTree: List<com.rk.ai.nativeagent.engine.SessionNode>,
    activeSessionId: kotlin.uuid.Uuid?,
    isProcessing: Boolean,
    onSwitchSession: (kotlin.uuid.Uuid) -> Unit,
    onNewBranch: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(sessionTree) { node ->
                val isActive = node.id == activeSessionId
                SessionTab(
                    title = node.title,
                    isActive = isActive,
                    isBranch = node.parentId != null,
                    onClick = { onSwitchSession(node.id) },
                )
            }
            item {
                FilledTonalIconButton(
                    onClick = onNewBranch,
                    modifier = Modifier.size(24.dp),
                    enabled = !isProcessing,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "New Branch",
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionTab(
    title: String,
    isActive: Boolean,
    isBranch: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isActive) MaterialTheme.colorScheme.surfaceContainerHigh
    else MaterialTheme.colorScheme.surfaceContainerLow

    val maxWidth = 160.dp

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = bg,
        modifier = Modifier.widthIn(max = maxWidth),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isBranch) {
                Icon(
                    Icons.Outlined.SubdirectoryArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(2.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(28.dp),
        enabled = enabled,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun PanelToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(28.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(14.dp),
            tint = if (isActive) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
