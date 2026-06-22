@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.nativeagent.engine.VibeCodingState
import com.rk.ai.nativeagent.ui.components.*
import com.rk.ai.nativeagent.ui.panels.*
import com.rk.ai.persistence.settings.getCurrentAssistant
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

/** Panels opened via ModalBottomSheet from the toolbar. */
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // UI state
    var showSettings by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showStopConfirmDialog by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showFiles by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var selectedInfoTab by remember { mutableStateOf<InfoTab?>(null) }
    var showAgentActivity by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf(ToolPanel.NONE) }
    var showUndoSnackbar by remember { mutableStateOf(false) }
    var sessionToRename by remember { mutableStateOf<kotlin.uuid.Uuid?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }

    val workspacePath by remember {
        derivedStateOf {
            try { engine.ideService.getPrimaryWorkspacePath() } catch (_: Exception) { "" }
        }
    }

    val hasTodos = state.todos.isNotEmpty() && state.dockOpen

    // ── Modal bottom sheets for tool panels ──
    if (activePanel != ToolPanel.NONE) {
        ModalBottomSheet(
            onDismissRequest = { activePanel = ToolPanel.NONE },
            sheetState = sheetState,
            sheetMaxWidth = androidx.compose.ui.unit.Dp.Unspecified,
            containerColor = colorScheme.surfaceContainerHigh,
            contentColor = colorScheme.onSurface,
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
                        onRefreshCommands = { engine.refreshCommands() },
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

    // ── Main scaffold ──
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
                    if (state.isProcessing) {
                        showStopConfirmDialog = true
                        true
                    } else false
                } else false
            },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        // Suggestions sidebar
        AnimatedVisibility(
            visible = showSuggestions,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
        ) {
            SuggestionPanel(
                engine = engine,
                onDismiss = { showSuggestions = false },
                modifier = Modifier.width(300.dp).fillMaxHeight(),
            )
        }

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
                    modifier = Modifier.width(260.dp).fillMaxHeight(),
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar
                VibeCodingToolbar(
                    engine = engine,
                    state = state,
                    colorScheme = colorScheme,
                    showFiles = showFiles,
                    showHistory = showHistory,
                    showSuggestions = showSuggestions,
                    showAgentActivity = showAgentActivity,
                    selectedInfoTab = selectedInfoTab,
                    onToggleFiles = { showFiles = !showFiles },
                    onToggleHistory = { showHistory = !showHistory },
                    onToggleSuggestions = { showSuggestions = !showSuggestions },
                    onToggleAgentActivity = { showAgentActivity = !showAgentActivity },
                    onSelectInfoTab = { selectedInfoTab = if (selectedInfoTab == it) null else it },
                    onOpenPanel = { activePanel = it },
                    onShowClearDialog = { showClearDialog = true },
                    onShowExportDialog = { showExportDialog = true },
                    onSettings = { showSettings = true },
                )

                // Session tabs
                if (state.sessionTree.isNotEmpty()) {
                    VibeCodingSessionTabs(
                        sessionTree = state.sessionTree,
                        activeSessionId = state.activeSessionId,
                        isProcessing = state.isProcessing,
                        onSwitchSession = { engine.switchToSession(it) },
                        onNewBranch = {
                            val parent = state.activeSessionId ?: return@VibeCodingSessionTabs
                            engine.createBranchSession(parent)
                        },
                        onRenameSession = { id, currentTitle ->
                            sessionToRename = id
                        },
                        onCloseSession = { id -> engine.closeSession(id) },
                    )
                }

                // Main content area
                Box(modifier = Modifier.weight(1f)) {
                    if (isTablet && selectedInfoTab != null) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.weight(0.65f)) {
                                VibeCodingContentStack(
                                    state = state,
                                    engine = engine,
                                    colorScheme = colorScheme,
                                    context = context,
                                    hasTodos = hasTodos,
                                    showAgentActivity = showAgentActivity,
                                    onClearTodos = {
                                        state.activeSessionId?.let { engine.setSessionTodos(it, emptyList()) }
                                    },
                                )
                            }
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                            Column(modifier = Modifier.weight(0.35f)) {
                                VibeCodingInfoPanel(
                                    selectedInfoTab = selectedInfoTab!!,
                                    state = state,
                                    engine = engine,
                                    colorScheme = colorScheme,
                                    onSelectTab = { selectedInfoTab = if (selectedInfoTab == it) null else it },
                                )
                                if (state.debugMode) {
                                    DebugPanel(
                                        debugInfo = state.debugInfo,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            VibeCodingContentStack(
                                state = state,
                                engine = engine,
                                colorScheme = colorScheme,
                                context = context,
                                hasTodos = hasTodos,
                                showAgentActivity = showAgentActivity,
                                onClearTodos = {
                                    state.activeSessionId?.let { engine.setSessionTodos(it, emptyList()) }
                                },
                            )
                            if (selectedInfoTab != null) {
                                VibeCodingInfoPanel(
                                    selectedInfoTab = selectedInfoTab!!,
                                    state = state,
                                    engine = engine,
                                    colorScheme = colorScheme,
                                    onSelectTab = { selectedInfoTab = if (selectedInfoTab == it) null else it },
                                )
                            }
                            if (state.debugMode) {
                                DebugPanel(
                                    debugInfo = state.debugInfo,
                                    modifier = Modifier.fillMaxWidth(),
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
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
                    onStop = {
                        if (state.toolExecutions.isNotEmpty() || state.taskTree != null) {
                            showStopConfirmDialog = true
                        } else {
                            engine.stopGeneration()
                        }
                    },
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
                modifier = Modifier.width(260.dp).fillMaxHeight(),
            )
        }
    }
    } // Scaffold

    // ── Undo snackbar ──
    LaunchedEffect(state.recentlyDeletedMessage) {
        if (state.recentlyDeletedMessage != null) {
            showUndoSnackbar = true
            val result = snackbarHostState.showSnackbar(
                message = "Message deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                engine.undoDeleteMessage()
            }
            showUndoSnackbar = false
        }
    }

    // ── Dialogs ──
    if (sessionToRename != null) {
        RenameSessionDialog(
            sessionId = sessionToRename!!,
            currentTitle = state.sessionTree.find { it.id == sessionToRename }?.title ?: "New Session",
            onRename = { id, title -> engine.renameSession(id, title) },
            onDismiss = { sessionToRename = null },
        )
    }

    if (showStopConfirmDialog) {
        StopConfirmDialog(
            onStop = { engine.stopGeneration() },
            onDismiss = { showStopConfirmDialog = false },
        )
    }

    if (showExportDialog) {
        ExportConversationDialog(
            state = state,
            onDismiss = { showExportDialog = false },
        )
    }

    if (showClearDialog) {
        ClearConversationDialog(
            onClear = { engine.clearConversation() },
            onDismiss = { showClearDialog = false },
        )
    }

    if (showSettings) {
        VibeCodingSettingsSheet(
            engine = engine,
            onDismiss = { showSettings = false },
        )
    }
}
