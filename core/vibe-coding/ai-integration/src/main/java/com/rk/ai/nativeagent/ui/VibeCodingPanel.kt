@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.agent.events.SessionTodo
import com.rk.ai.agent.events.SessionTodoStatus
import com.rk.ai.nativeagent.engine.AgentActivity
import com.rk.ai.nativeagent.engine.SessionNode
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.nativeagent.engine.VibeCodingState
import com.rk.ai.nativeagent.ui.components.*
import com.rk.ai.nativeagent.ui.panels.*
import com.rk.ai.persistence.settings.getCurrentAssistant

import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

enum class ToolPanel {
    NONE, COMMANDS, SKILLS, AGENTS, PERMISSIONS, INSTRUCTIONS, PLUGINS
}

private enum class InfoTab { PLAN, TOOLS, CONTEXT, CHANGES }

private enum class OverflowAction {
    SKILLS, AGENTS, RULES, PLUGINS, PERMS, CLEAR
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
    var selectedInfoTab by remember { mutableStateOf<InfoTab?>(null) }
    var showAgentActivity by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf(ToolPanel.NONE) }
    var showOverflow by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val workspacePath = remember {
        try {
            engine.ideService.getPrimaryWorkspacePath()
        } catch (_: Exception) { "/" }
    }

    val hasTodos = state.todos.isNotEmpty() && state.dockOpen

    // Build list of visible info panels
    val visibleInfoTabs = remember(selectedInfoTab) {
        buildList {
            add(InfoTab.PLAN)
            add(InfoTab.TOOLS)
            add(InfoTab.CONTEXT)
            add(InfoTab.CHANGES)
        }
    }

    // Current tab index into visible list
    val currentInfoTabIndex = remember(selectedInfoTab, visibleInfoTabs) {
        selectedInfoTab?.let { visibleInfoTabs.indexOf(it).coerceAtLeast(0) } ?: 0
    }

    if (activePanel != ToolPanel.NONE) {
        ModalBottomSheet(
            onDismissRequest = { activePanel = ToolPanel.NONE },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
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
                ToolbarSection(
                    engine = engine,
                    state = state,
                    colorScheme = colorScheme,
                    showFiles = showFiles,
                    showHistory = showHistory,
                    showAgentActivity = showAgentActivity,
                    selectedInfoTab = selectedInfoTab,
                    showOverflow = showOverflow,
                    onToggleFiles = { showFiles = !showFiles },
                    onToggleHistory = { showHistory = !showHistory },
                    onToggleAgentActivity = { showAgentActivity = !showAgentActivity },
                    onSelectInfoTab = { selectedInfoTab = if (selectedInfoTab == it) null else it },
                    onOpenPanel = { activePanel = it },
                    onOverflowAction = { action ->
                        showOverflow = false
                        when (action) {
                            OverflowAction.SKILLS -> activePanel = ToolPanel.SKILLS
                            OverflowAction.AGENTS -> activePanel = ToolPanel.AGENTS
                            OverflowAction.RULES -> activePanel = ToolPanel.INSTRUCTIONS
                            OverflowAction.PLUGINS -> activePanel = ToolPanel.PLUGINS
                            OverflowAction.PERMS -> activePanel = ToolPanel.PERMISSIONS
                            OverflowAction.CLEAR -> showClearDialog = true
                        }
                    },
                    onToggleOverflow = { showOverflow = !showOverflow },
                    onSettings = { showSettings = true },
                )

                // Session tabs
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

                // Main content area
                Box(modifier = Modifier.weight(1f)) {
                    Column {
                        // Security alerts
                        if (state.hasSecurityAlerts) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp),
                            ) {
                                items(state.securityAlerts.takeLast(3)) { alert ->
                                    SecurityAlertBanner(
                                        alert = alert,
                                        onDismiss = { engine.clearSecurityAlerts() },
                                    )
                                }
                            }
                        }

                        // Todo panel
                        AnimatedVisibility(
                            visible = hasTodos,
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut(),
                        ) {
                            TodoPanel(
                                todos = state.todos,
                                completedCount = state.completedTodos,
                                onClear = {
                                    val sessionId = state.activeSessionId
                                    if (sessionId != null) {
                                        engine.setSessionTodos(sessionId, emptyList())
                                    }
                                },
                                colorScheme = colorScheme,
                            )
                        }

                        // Agent activity
                        AnimatedVisibility(
                            visible = showAgentActivity && state.agentActivities.isNotEmpty(),
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut(),
                        ) {
                            AgentActivitySection(
                                activities = state.agentActivities,
                                colorScheme = colorScheme,
                            )
                        }

                        // Info panels (Plan, Tools, Context, Changes)
                        if (selectedInfoTab != null) {
                            InfoPanelSection(
                                selectedInfoTab = selectedInfoTab!!,
                                visibleTabs = visibleInfoTabs,
                                currentTabIndex = currentInfoTabIndex,
                                state = state,
                                engine = engine,
                                colorScheme = colorScheme,
                                onSelectTab = { selectedInfoTab = if (selectedInfoTab == it) null else it },
                            )
                        }

                        // Main message list
                        Box(modifier = Modifier.weight(1f)) {
                            if (state.messages.isEmpty()) {
                                EmptyState(colorScheme)
                            } else {
                                VibeCodingMessageList(
                                    messages = state.messages,
                                    isProcessing = state.isProcessing,
                                    onApproveTool = { toolCallId -> engine.approveTool(toolCallId) },
                                    onDenyTool = { toolCallId, reason -> engine.denyTool(toolCallId, reason) },
                                    onAnswerTool = { toolCallId, answer -> engine.answerTool(toolCallId, answer) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }

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
private fun ToolbarSection(
    engine: VibeCodingEngine,
    state: VibeCodingState,
    colorScheme: ColorScheme,
    showFiles: Boolean,
    showHistory: Boolean,
    showAgentActivity: Boolean,
    selectedInfoTab: InfoTab?,
    showOverflow: Boolean,
    onToggleFiles: () -> Unit,
    onToggleHistory: () -> Unit,
    onToggleAgentActivity: () -> Unit,
    onSelectInfoTab: (InfoTab) -> Unit,
    onOpenPanel: (ToolPanel) -> Unit,
    onOverflowAction: (OverflowAction) -> Unit,
    onToggleOverflow: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainerLow,
        tonalElevation = 0.5.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Model name label
                val settings by engine.settingsStore.settingsFlow.collectAsState()
                val modelName = remember(settings.chatModelId, settings.providers) {
                    val model = settings.providers.flatMap { it.models }
                        .firstOrNull { it.id == settings.chatModelId }
                    model?.displayName?.ifEmpty { model.modelId } ?: "No model"
                }

                Text(
                    text = modelName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 100.dp),
                )

                Spacer(Modifier.width(4.dp))

                HorizontalDivider(
                    modifier = Modifier.height(16.dp).width(1.dp),
                    color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                )

                Spacer(Modifier.width(4.dp))

                // Primary actions
                ToolbarChip(
                    icon = Icons.Outlined.Terminal,
                    label = "Commands",
                    onClick = { onOpenPanel(ToolPanel.COMMANDS) },
                    colorScheme = colorScheme,
                )

                ToolbarChip(
                    icon = Icons.Outlined.Code,
                    label = "Files",
                    isActive = showFiles,
                    onClick = onToggleFiles,
                    colorScheme = colorScheme,
                )

                ToolbarChip(
                    icon = Icons.Outlined.List,
                    label = "History",
                    isActive = showHistory,
                    onClick = onToggleHistory,
                    colorScheme = colorScheme,
                )

                // Info panel toggles
                ToolbarToggleButton(
                    icon = Icons.Outlined.Checklist,
                    label = "Plan",
                    isActive = selectedInfoTab == InfoTab.PLAN,
                    onClick = { onSelectInfoTab(InfoTab.PLAN) },
                    colorScheme = colorScheme,
                )

                ToolbarToggleButton(
                    icon = Icons.Outlined.Build,
                    label = "Tools",
                    isActive = selectedInfoTab == InfoTab.TOOLS,
                    onClick = { onSelectInfoTab(InfoTab.TOOLS) },
                    colorScheme = colorScheme,
                )

                ToolbarToggleButton(
                    icon = Icons.Outlined.Info,
                    label = "Context",
                    isActive = selectedInfoTab == InfoTab.CONTEXT,
                    onClick = { onSelectInfoTab(InfoTab.CONTEXT) },
                    colorScheme = colorScheme,
                )

                ToolbarToggleButton(
                    icon = Icons.Outlined.Folder,
                    label = "Changes",
                    isActive = selectedInfoTab == InfoTab.CHANGES,
                    onClick = { onSelectInfoTab(InfoTab.CHANGES) },
                    colorScheme = colorScheme,
                )

                ToolbarToggleButton(
                    icon = Icons.Outlined.Notifications,
                    label = "Agent",
                    isActive = showAgentActivity,
                    onClick = onToggleAgentActivity,
                    colorScheme = colorScheme,
                )

                Spacer(Modifier.width(4.dp))

                HorizontalDivider(
                    modifier = Modifier.height(16.dp).width(1.dp),
                    color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                )

                Spacer(Modifier.width(4.dp))

                // Settings
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(16.dp),
                    )
                }

                // Overflow menu (⋮)
                Box {
                    IconButton(
                        onClick = onToggleOverflow,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "More",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = onToggleOverflow,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Skills", style = MaterialTheme.typography.bodySmall) },
                            onClick = { onOverflowAction(OverflowAction.SKILLS) },
                            leadingIcon = {
                                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Agents", style = MaterialTheme.typography.bodySmall) },
                            onClick = { onOverflowAction(OverflowAction.AGENTS) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Psychology, null, Modifier.size(16.dp))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Rules", style = MaterialTheme.typography.bodySmall) },
                            onClick = { onOverflowAction(OverflowAction.RULES) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Description, null, Modifier.size(16.dp))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Plugins", style = MaterialTheme.typography.bodySmall) },
                            onClick = { onOverflowAction(OverflowAction.PLUGINS) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Extension, null, Modifier.size(16.dp))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Permissions", style = MaterialTheme.typography.bodySmall) },
                            onClick = { onOverflowAction(OverflowAction.PERMS) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Security, null, Modifier.size(16.dp))
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text("Clear chat", color = colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            },
                            onClick = { onOverflowAction(OverflowAction.CLEAR) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp), tint = colorScheme.error)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarChip(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
    colorScheme: ColorScheme,
) {
    val bg = if (isActive) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
    val content = if (isActive) colorScheme.onPrimaryContainer else colorScheme.onSurface

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(14.dp), tint = content)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ToolbarToggleButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    colorScheme: ColorScheme,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(28.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(14.dp),
            tint = if (isActive) colorScheme.primary else colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodoPanel(
    todos: List<SessionTodo>,
    completedCount: Int,
    onClear: () -> Unit,
    colorScheme: ColorScheme,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
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
                    text = "Tasks ($completedCount/${todos.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            todos.take(3).forEach { todo ->
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

@Composable
private fun AgentActivitySection(
    activities: List<AgentActivity>,
    colorScheme: ColorScheme,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 160.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(activities.takeLast(5)) { activity ->
            AgentActivityCard(activity = activity)
        }
    }
}

@Composable
private fun InfoPanelSection(
    selectedInfoTab: InfoTab,
    visibleTabs: List<InfoTab>,
    currentTabIndex: Int,
    state: VibeCodingState,
    engine: VibeCodingEngine,
    colorScheme: ColorScheme,
    onSelectTab: (InfoTab) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.surfaceContainerLowest,
    ) {
        Column {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(visibleTabs) { tab ->
                    val label = tab.name.lowercase().replaceFirstChar { it.uppercase() }
                    FilterChip(
                        selected = tab == selectedInfoTab,
                        onClick = { onSelectTab(tab) },
                        label = {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        },
                        modifier = Modifier.height(24.dp),
                    )
                }
            }

            when (selectedInfoTab) {
                InfoTab.PLAN -> ExecutionGraphPanel(
                    taskTree = state.taskTree,
                    modifier = Modifier.weight(1f),
                )
                InfoTab.TOOLS -> ToolActivityPanel(
                    toolExecutions = state.toolExecutions,
                    modifier = Modifier.weight(1f),
                )
                InfoTab.CONTEXT -> {
                    val contextInfo = ContextInfo(
                        currentGoal = engine.contextMemoryManager.conversation.getCurrentGoal(),
                        modifiedFiles = state.modifiedFiles,
                        projectIndexed = state.projectIndexed,
                        toolCalls = state.toolExecutions.size,
                    )
                    ContextPanel(info = contextInfo, modifier = Modifier.weight(1f))
                }
                InfoTab.CHANGES -> FileChangePanel(
                    modifiedFiles = state.modifiedFiles,
                    currentPhase = state.currentPhase,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    colorScheme: ColorScheme,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.AutoFixHigh,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
            Text(
                text = "VibeCoding Ready",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Ask a question or describe what you want to build",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun SessionTabsRow(
    sessionTree: List<SessionNode>,
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

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = bg,
        modifier = Modifier.widthIn(max = 160.dp),
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
