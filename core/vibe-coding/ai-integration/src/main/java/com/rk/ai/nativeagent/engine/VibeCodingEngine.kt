@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.engine

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.rk.ai.agent.GenerationChunk
import com.rk.ai.agent.AILoggingManager
import com.rk.ai.agent.AppEventBus
import com.rk.ai.agent.GenerationHandler
import com.rk.ai.agent.events.SessionTodo
import com.rk.ai.agent.events.SessionTodoStatus
import com.rk.ai.agent.events.VibeCodingEvent
import com.rk.ai.agent.events.VibeCodingEventBus
import com.rk.ai.agent.files.CommandFileLoader
import com.rk.ai.agent.files.ConfigProvider
import com.rk.ai.agent.files.DefaultContentSeeder
import com.rk.ai.agent.files.FilesManager
import com.rk.ai.agent.files.SkillManager
import com.rk.ai.agent.executor.AgentOrchestrator
import com.rk.ai.agent.executor.AgentPhase
import com.rk.ai.agent.executor.ExecutionEngine
import com.rk.ai.agent.files.UnifiedConfig
import com.rk.ai.agent.files.XedConfig
import com.rk.ai.agent.files.XedConfigLoader
import com.rk.ai.agent.indexer.ProjectIndexer
import com.rk.ai.agent.tools.ToolValidator
import com.rk.ai.agent.agents.AgentResult
import com.rk.ai.agent.hooks.HookContext
import com.rk.ai.agent.hooks.HookEvent
import com.rk.ai.agent.hooks.HookManager
import com.rk.ai.agent.hooks.HookResult
import com.rk.ai.agent.hooks.SecurityHook
import com.rk.ai.agent.tools.LocalTools
import com.rk.ai.agent.tools.ToolCache
import com.rk.ai.agent.tools.ToolRouter
import com.rk.ai.agent.tools.VibeCodingSystemTools
import com.rk.ai.agent.tools.VibeCodingToolRegistry
import com.rk.ai.agent.transformers.Base64ImageToLocalFileTransformer
import com.rk.ai.agent.transformers.InputMessageTransformer
import com.rk.ai.agent.transformers.PlaceholderTransformer
import com.rk.ai.agent.transformers.PromptInjectionTransformer
import com.rk.ai.agent.transformers.RegexOutputTransformer
import com.rk.ai.agent.transformers.TimeReminderTransformer
import com.rk.ai.agent.transformers.ToolTagSanitizerTransformer
import com.rk.ai.agent.transformers.TransformerContext
import com.rk.ai.agent.tools.createSearchTools
import com.rk.ai.agent.tools.createSkillTools
import com.rk.ai.agent.tools.SuggestionStore
import com.rk.ai.agent.agents.AgentRegistry
import com.rk.ai.agent.context.ContextMemoryManager
import com.rk.ai.agent.planner.TaskPlanner
import com.rk.ai.core.AppScope
import com.rk.ai.mcp.FileManager
import com.rk.ai.mcp.McpManager
import com.rk.ai.models.InputSchema
import com.rk.ai.models.McpTool
import com.rk.ai.models.Tool
import com.rk.ai.models.ToolApprovalState
import com.rk.ai.core.MessageRole
import com.rk.ai.models.UIMessage
import com.rk.ai.models.UIMessagePart
import com.rk.ai.models.toMessageNode
import com.rk.ai.persistence.db.AppDatabase
import com.rk.ai.persistence.db.fts.MessageFtsManager
import com.rk.ai.persistence.repo.ConversationRepository
import com.rk.ai.persistence.repo.FilesRepository
import com.rk.ai.persistence.repo.MemoryRepository
import com.rk.ai.persistence.settings.SettingsStore
import com.rk.ai.persistence.settings.findModelById
import com.rk.ai.persistence.settings.getCurrentAssistant
import com.rk.ai.providers.ProviderManager
import com.rk.ai.service.IdeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "VibeCodingEngine"
private val defaultJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun buildOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.MINUTES)
    .build()

private fun buildDatabase(context: Context): AppDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "vibecoding_db",
    ).fallbackToDestructiveMigration().build()

private val shutdownHooksRegistered = java.util.concurrent.atomic.AtomicBoolean(false)
private fun registerShutdownHookForDatabase(db: AppDatabase) {
    if (shutdownHooksRegistered.compareAndSet(false, true)) {
        Runtime.getRuntime().addShutdownHook(Thread {
            try { db.close() } catch (_: Exception) { }
        })
    }
}

class VibeCodingEngine(
    private val context: Context,
    val ideService: IdeService,
    scope: CoroutineScope? = null,
    private val json: Json = defaultJson,
    okHttpClient: OkHttpClient = buildOkHttpClient(),
) {
    val suggestionsFlow: MutableStateFlow<List<kotlinx.serialization.json.JsonObject>> = MutableStateFlow(emptyList())
    private val engineScope: CoroutineScope =
        scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appScope = AppScope()

    val database = buildDatabase(context)
    private val memoryRepo = MemoryRepository(database.memoryDao())
    private val conversationRepo = ConversationRepository(
        conversationDAO = database.conversationDao(),
        messageNodeDAO = database.messageNodeDao(),
        favoriteDAO = database.favoriteDao(),
        database = database,
        messageFtsManager = MessageFtsManager(database),
    )
    private val filesRepo = FilesRepository(database.managedFileDao())

    val providerManager = ProviderManager(okHttpClient, context)
    val settingsStore = SettingsStore(context, appScope)

    private val eventBus = AppEventBus()
    private val filesManager = FilesManager(context, filesRepo, appScope)
    private val mcpManager = McpManager(settingsStore, appScope, VibeCodingFileManager(context))
    private     val skillManager = SkillManager(context, settingsStore)
    private val localTools = LocalTools(context, eventBus)

    val vibeEventBus = VibeCodingEventBus()
    val contextMemoryManager = ContextMemoryManager()

    val generationHandler = GenerationHandler(
        context = context,
        providerManager = providerManager,
        json = json,
        memoryRepo = memoryRepo,
        conversationRepo = conversationRepo,
        aiLoggingManager = AILoggingManager(),
        contextMemory = contextMemoryManager,
    )

    val toolRegistry = VibeCodingToolRegistry(ideService, context, providerManager, settingsStore)
    val hookManager = HookManager()
    val permissionManager = PermissionManager()

    val generationPipeline = GenerationPipeline(
        generationHandler = generationHandler,
        permissionManager = permissionManager,
        vibeEventBus = vibeEventBus,
        engineScope = engineScope,
        onStateUpdate = { transform -> _state.value = _state.value.transform() },
        onSaveSession = { saveCurrentSessionMessages() },
        onSaveConversation = suspend { saveConversation() },
        getState = { _state.value },
    )

    val toolCache = ToolCache()
    val toolRouter = ToolRouter(toolCache, null)
    val projectIndexer = ProjectIndexer(ideService)
    val executionEngine = ExecutionEngine(
        ideService = ideService,
        contextMemory = contextMemoryManager,
        toolCache = toolCache,
        toolRouter = toolRouter,
        projectIndexer = projectIndexer,
    )

    val orchestrator = AgentOrchestrator(
        ideService = ideService,
        contextMemory = contextMemoryManager,
        toolCache = toolCache,
        toolRouter = toolRouter,
        executionEngine = executionEngine,
        projectIndexer = projectIndexer,
    )

    val agentRegistry = AgentRegistry(context, ideService, providerManager, settingsStore)

    private val storedCommandCatalog = mutableListOf<CommandCatalogEntry>()
    private var xedConfig = XedConfig()
    private val toolValidator = ToolValidator()
    val configProvider = ConfigProvider(
        context = context,
        settingsStore = settingsStore,
        workspacePath = { ideService.getPrimaryWorkspacePath() },
        scope = engineScope,
    )

    private var sessionCounter = 0L

    private val _state = MutableStateFlow(VibeCodingState())
    val state: StateFlow<VibeCodingState> = _state.asStateFlow()

    init {
        registerShutdownHookForDatabase(database)
        _state.value = _state.value.copy(
            permissionAutoRespondRules = permissionManager.rules,
        )
        configProvider.unifiedConfig.value.let { cfg ->
            applyPermissionRules(cfg)
        }
        engineScope.launch {
            configProvider.unifiedConfig.collect { cfg ->
                applyPermissionRules(cfg)
            }
        }
        engineScope.launch {
            SuggestionStore.suggestions.collect { suggestionsFlow.value = it }
        }
        val securityHook = SecurityHook { severity, message, toolName, filePath ->
            val alert = SecurityAlert(severity, message, toolName, filePath)
            addSecurityAlert(alert)
            engineScope.launch {
                vibeEventBus.emit(VibeCodingEvent.SecurityAlert(
                    severity = severity,
                    message = message,
                    toolName = toolName,
                    filePath = filePath,
                ))
            }
        }
        hookManager.register(HookEvent.BEFORE_FILE_WRITE, securityHook)
        hookManager.register(HookEvent.BEFORE_FILE_EDIT, securityHook)

        toolRegistry.onAgentResult = { name, result ->
            val status = when (result) {
                is AgentResult.Success -> AgentActivityStatus.COMPLETED
                is AgentResult.Failure -> AgentActivityStatus.FAILED
                is AgentResult.NotAttempted -> AgentActivityStatus.PENDING
            }
            updateAgentActivity(name, status, result)
        }

        DefaultContentSeeder.seedIfNeeded(context)
        loadFileCommandsIntoCatalog()
        loadProjectConfig()

        orchestrator.setPhaseChangeListener { phase ->
            _state.value = _state.value.copy(currentPhase = phase)
        }
    }

    fun loadProjectConfig() {
        val workspace = try {
            ideService.getPrimaryWorkspacePath()
        } catch (_: Exception) { return }
        xedConfig = XedConfigLoader.loadConfig(workspace)
        applyConfigPermissions()
        xedConfig.instructions?.let {
            if (it.isNotBlank()) {
                systemPromptBuilder.projectInstructions = it
            }
        }
    }

    fun refreshProjectConfig() {
        systemPromptBuilder.reset()
        loadProjectConfig()
        loadFileCommandsIntoCatalog()
    }

    fun isToolEnabled(toolName: String): Boolean {
        return xedConfig.tools[toolName] ?: true
    }

    fun applyConfigPermissions() {
        for (rule in xedConfig.permission) {
            val action = when (rule.action.lowercase()) {
                "allow" -> PermissionAction.ALLOW
                "deny" -> PermissionAction.DENY
                else -> PermissionAction.ASK
            }
            permissionManager.addRule(PermissionAutoRespondRule(
                toolPattern = rule.tool,
                argPattern = rule.arg,
                action = action,
                description = rule.description,
            ))
        }
    }

    fun loadFileCommandsIntoCatalog() {
        val fileCommands = CommandFileLoader.listCommands(context)
        for (cmd in fileCommands) {
            if (cmd.hidden) continue
            addCommandToCatalog(CommandCatalogEntry(
                id = "file:${cmd.id}",
                title = cmd.name,
                description = cmd.description,
                category = cmd.category,
                slash = cmd.id,
                prompt = cmd.prompt,
            ))
        }
    }

    suspend fun evaluateHooks(event: HookEvent, context: HookContext): HookResult {
        return hookManager.checkAll(event, context)
    }

    fun addPermissionAutoRespondRule(rule: PermissionAutoRespondRule) {
        permissionManager.addRule(rule)
        _state.value = _state.value.copy(
            permissionAutoRespondRules = permissionManager.rules,
        )
    }

    fun removePermissionAutoRespondRule(idOrPattern: String) {
        permissionManager.removeRule(idOrPattern)
        _state.value = _state.value.copy(
            permissionAutoRespondRules = permissionManager.rules,
        )
    }

    fun addCommandToCatalog(entry: CommandCatalogEntry) {
        storedCommandCatalog.removeAll { it.id == entry.id }
        storedCommandCatalog.add(entry)
        _state.value = _state.value.copy(
            commandCatalog = storedCommandCatalog.toList(),
        )
    }

    fun removeCommandFromCatalog(id: String) {
        storedCommandCatalog.removeAll { it.id == id }
        _state.value = _state.value.copy(
            commandCatalog = storedCommandCatalog.toList(),
        )
    }

    fun getCommandCatalog(): List<CommandCatalogEntry> = storedCommandCatalog.toList()

    fun setSessionTodos(sessionId: Uuid, todos: List<SessionTodo>) {
        _state.value = _state.value.copy(todos = todos)
        engineScope.launch {
            vibeEventBus.emit(VibeCodingEvent.TodoUpdated(sessionId, todos))
        }
    }

    private val todowriteTool = Tool(
        name = "todowrite",
        description = "Create and manage a structured task list for the current session. Use this to break down complex tasks into tracked subtasks. Each todo has a description and status (pending/in_progress/completed/cancelled). Call this at the start of multi-step work to create a plan, then update status as you complete each step. Pass an empty array to read the current todos.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("todos") {
                        put("type", "string")
                        put("description", "JSON array of todos. Each item: {\"description\": \"...\", \"status\": \"pending\"}. Options for status: pending, in_progress, completed, cancelled. Example: [{\"description\": \"Read the main file\", \"status\": \"pending\"}, {\"description\": \"Implement the fix\", \"status\": \"pending\"}]")
                    }
                },
                required = listOf("todos"),
            )
        },
        execute = { args ->
            val todosElement = args.asJsonObject["todos"]
                ?: return@Tool listOf(UIMessagePart.Text("Error: missing required argument 'todos'"))
            val todosJson = when {
                todosElement.isJsonArray -> todosElement.asJsonArray
                todosElement.isJsonPrimitive && todosElement.asJsonPrimitive.isString -> {
                    try {
                        com.google.gson.JsonParser.parseString(todosElement.asString).asJsonArray
                    } catch (e: Exception) {
                        return@Tool listOf(UIMessagePart.Text("Error: invalid JSON in 'todos': ${e.message}"))
                    }
                }
                else -> return@Tool listOf(UIMessagePart.Text("Error: 'todos' must be a JSON array or a JSON string"))
            }
            val todos = todosJson.mapIndexed { index, item ->
                val obj = item.asJsonObject
                val desc = obj["description"]?.asJsonPrimitive?.asString ?: "Untitled task"
                val statusStr = obj["status"]?.asJsonPrimitive?.asString ?: "pending"
                val status = when (statusStr.lowercase()) {
                    "in_progress" -> SessionTodoStatus.IN_PROGRESS
                    "completed" -> SessionTodoStatus.COMPLETED
                    "cancelled" -> SessionTodoStatus.CANCELLED
                    else -> SessionTodoStatus.PENDING
                }
                val id = obj["id"]?.asJsonPrimitive?.asString ?: "todo-${Uuid.random()}"
                SessionTodo(id = id, description = desc, status = status)
            }

            val currentTodos = _state.value.todos
            val isReadOp = todos.isEmpty()
            val sessionId = _state.value.activeSessionId ?: Uuid.random()

            if (!isReadOp) {
                setSessionTodos(sessionId, todos)
            }

            val displayTodos = if (isReadOp) currentTodos else todos
            val summary = buildString {
                if (isReadOp) {
                    appendLine("Current task plan (${displayTodos.size} items):")
                } else {
                    appendLine("Task plan updated (${displayTodos.size} items):")
                }
                displayTodos.forEachIndexed { i, todo ->
                    val icon = when (todo.status) {
                        SessionTodoStatus.COMPLETED -> "[✓]"
                        SessionTodoStatus.IN_PROGRESS -> "[→]"
                        SessionTodoStatus.CANCELLED -> "[✗]"
                        SessionTodoStatus.PENDING -> "[ ]"
                    }
                    appendLine("  $icon ${i + 1}. ${todo.description}")
                }
                appendLine()
                val completed = displayTodos.count { it.status == SessionTodoStatus.COMPLETED }
                val inProgress = displayTodos.count { it.status == SessionTodoStatus.IN_PROGRESS }
                appendLine("Progress: $completed/${displayTodos.size} completed, $inProgress in progress")
            }
            listOf(UIMessagePart.Text(summary))
        },
    )

    fun refreshCommands() {
        storedCommandCatalog.removeAll { it.id.startsWith("file:") }
        loadFileCommandsIntoCatalog()
        _state.value = _state.value.copy(commandCatalog = storedCommandCatalog.toList())
    }

    private val listCustomCommandsTool = Tool(
        name = "listCustomCommands",
        description = "Lists all custom commands loaded from .xed/commands/. These are user-defined or project-specific commands that can be executed by invoking their prompt template.",
        execute = { _ ->
            val customCmds = storedCommandCatalog.filter { it.id.startsWith("file:") }
            val text = buildString {
                if (customCmds.isEmpty()) {
                    appendLine("No custom commands found. Add .md files to .xed/commands/ to create custom commands.")
                } else {
                    appendLine("Custom commands (${customCmds.size}):")
                    customCmds.forEach { cmd ->
                        appendLine("  /${cmd.slash} - ${cmd.title}")
                        appendLine("    ${cmd.description}")
                        appendLine()
                    }
                    appendLine("Use the prompt content of a command as a template for your own tasks.")
                }
            }
            listOf(UIMessagePart.Text(text))
        },
    )

    private val planTool = Tool(
        name = "plan",
        description = "Create a structured multi-step execution plan. Call this BEFORE starting complex multi-file tasks. The plan creates a tracked todo list and returns a clear step-by-step breakdown. Each step should be specific and actionable.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("goal") { put("type", "string"); put("description", "The overall goal of this plan") }
                    putJsonObject("steps") {
                        put("type", "string")
                        put("description", "JSON array of step strings describing each action. Example: [\"Read the current implementation in src/Foo.kt\", \"Update the Foo class to support the new feature\", \"Add tests for the new functionality\", \"Run the test suite to verify\"]")
                    }
                },
                required = listOf("goal", "steps"),
            )
        },
        execute = { args ->
            val goal = args.asJsonObject["goal"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Error: missing required argument 'goal'"))
            val stepsElement = args.asJsonObject["steps"]
                ?: return@Tool listOf(UIMessagePart.Text("Error: missing required argument 'steps'"))
            val stepsJson = when {
                stepsElement.isJsonArray -> stepsElement.asJsonArray
                stepsElement.isJsonPrimitive && stepsElement.asJsonPrimitive.isString -> {
                    try {
                        com.google.gson.JsonParser.parseString(stepsElement.asString).asJsonArray
                    } catch (e: Exception) {
                        return@Tool listOf(UIMessagePart.Text("Error: invalid JSON in 'steps': ${e.message}"))
                    }
                }
                else -> return@Tool listOf(UIMessagePart.Text("Error: 'steps' must be a JSON array or a JSON string"))
            }

            val todos = stepsJson.mapIndexed { index, step ->
                SessionTodo(id = "step-${Uuid.random()}", description = step.asString, status = SessionTodoStatus.PENDING)
            }

            val sessionId = _state.value.activeSessionId ?: Uuid.random()
            setSessionTodos(sessionId, todos)

            val planText = buildString {
                appendLine("## Plan: $goal")
                appendLine()
                todos.forEachIndexed { i, todo ->
                    appendLine("  [ ] Step ${i + 1}: ${todo.description}")
                }
                appendLine()
                appendLine("---")
                appendLine("Total: ${todos.size} steps")
                appendLine("Start with Step 1. Update progress with `todowrite` after completing each step.")
            }
            listOf(UIMessagePart.Text(planText))
        },
    )

    private fun applyPermissionRules(cfg: UnifiedConfig) {
        for (rule in cfg.permissionRules) {
            val action = when (rule.action.lowercase()) {
                "allow" -> PermissionAction.ALLOW
                "deny" -> PermissionAction.DENY
                else -> PermissionAction.ASK
            }
            permissionManager.addRule(
                PermissionAutoRespondRule(
                    toolPattern = rule.toolPattern,
                    argPattern = rule.argPattern,
                    action = action,
                    description = rule.description,
                )
            )
        }
    }

    private fun buildToolList(assistant: com.rk.ai.models.Assistant, settings: com.rk.ai.persistence.settings.Settings): List<Tool> {
        val mcpTools = mcpManager.getAllAvailableTools().map { (serverId, mcpTool) ->
            Tool(
                name = mcpTool.name,
                description = mcpTool.description ?: "",
                parameters = mcpTool.inputSchema?.let { schema ->
                    { schema }
                } ?: { InputSchema.Obj(kotlinx.serialization.json.buildJsonObject { }) },
                execute = { args ->
                    val argsStr = try {
                        com.google.gson.Gson().toJson(args)
                    } catch (_: Exception) {
                        args.toString()
                    }
                    val kotlinxArgs = json.parseToJsonElement(argsStr).jsonObject
                    toolValidator.validateWithSchema(
                        toolName = mcpTool.name,
                        schema = mcpTool.inputSchema,
                        args = args,
                    )
                    mcpManager.callTool(serverId, mcpTool.name, kotlinxArgs)
                },
            )
        }
        val baseTools = buildList {
            addAll(toolRegistry.withMcpTools(mcpTools))
            addAll(localTools.getTools(assistant.localTools))
            if (settings.enableWebSearch) addAll(createSearchTools(settings))
            addAll(createSkillTools(
                enabledSkills = assistant.enabledSkills,
                allSkills = skillManager.listSkills(),
                skillManager = skillManager,
            ))
            add(todowriteTool)
            add(planTool)
            add(listCustomCommandsTool)
        }

        val cfg = configProvider.unifiedConfig.value
        return baseTools
            .filter { tool -> cfg.isToolEnabled(tool.name) }
            .map { tool ->
                permissionManager.wrapToolWithPermissionCheck(tool) { _state.value }
            }
    }

    fun dispose() {
        generationPipeline.cancel()
        appScope.coroutineContext[Job]?.cancel()
        database.close()
    }

    fun getCurrentAssistantId(): Uuid {
        val settings = settingsStore.settingsFlow.value
        return runCatching { settings.getCurrentAssistant().id }.getOrElse {
            Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
        }
    }

    fun openFileInEditor(path: String) {
        ideService.openFile(java.io.File(path))
    }

    fun trackAgentActivity(activity: AgentActivity) {
        _state.value = _state.value.copy(
            agentActivities = _state.value.agentActivities + activity,
        )
    }

    fun updateAgentActivity(agentName: String, status: AgentActivityStatus, result: AgentResult? = null) {
        val activities = _state.value.agentActivities.toMutableList()
        val idx = activities.indexOfLast { it.agentName == agentName && it.status == AgentActivityStatus.RUNNING }
        if (idx >= 0) {
            activities[idx] = activities[idx].copy(
                status = status,
                result = result,
                completedAt = if (status == AgentActivityStatus.COMPLETED || status == AgentActivityStatus.FAILED)
                    System.currentTimeMillis() else null,
            )
            _state.value = _state.value.copy(agentActivities = activities)
        }
    }

    fun addSecurityAlert(alert: SecurityAlert) {
        _state.value = _state.value.copy(
            securityAlerts = _state.value.securityAlerts + alert,
        )
    }

    fun clearSecurityAlerts() {
        _state.value = _state.value.copy(securityAlerts = emptyList())
    }

    private val systemPromptBuilder = SystemPromptBuilder(ideService)

    private inner class SystemPromptTransformer : InputMessageTransformer {
        override suspend fun transform(
            ctx: TransformerContext,
            messages: List<UIMessage>,
        ): List<UIMessage> {
            if (systemPromptBuilder.isInjected() || messages.isEmpty()) return messages
            val workspaceContext = systemPromptBuilder.build(ctx.model)
            return listOf(UIMessage.system(workspaceContext.toString())) + messages
        }

        fun reset() { systemPromptBuilder.reset() }
    }

    private val systemPromptTransformer = SystemPromptTransformer()

    val messages: List<UIMessage> get() = _state.value.messages
    val isProcessing: Boolean get() = _state.value.isProcessing

    fun createBranchSession(parentSessionId: Uuid, title: String = "Branch"): Uuid {
        val newId = Uuid.random()
        val node = SessionNode(
            id = newId,
            parentId = parentSessionId,
            title = title,
        )
        _state.value = _state.value.copy(
            sessionTree = _state.value.sessionTree + node,
            activeSessionId = newId,
            parentSessionId = parentSessionId,
        )
        engineScope.launch {
            vibeEventBus.emit(VibeCodingEvent.SessionCreated(newId, parentSessionId))
        }
        return newId
    }

    fun switchToSession(sessionId: Uuid) {
        val node = _state.value.sessionById[sessionId] ?: return
        _state.value = _state.value.copy(
            messages = node.messages,
            activeSessionId = sessionId,
            parentSessionId = node.parentId,
        )
    }

    private fun saveCurrentSessionMessages() {
        val sessionId = _state.value.activeSessionId ?: return
        val tree = _state.value.sessionTree.toMutableList()
        val idx = tree.indexOfFirst { it.id == sessionId }
        if (idx >= 0) {
            tree[idx] = tree[idx].copy(messages = _state.value.messages)
            _state.value = _state.value.copy(sessionTree = tree)
        }
    }

    fun sendMessage(text: String, extraParts: List<UIMessagePart> = emptyList()) {
        ensureSessionExists(text.trim())
        val trimmed = text.trim()
        // Route complex multi-step tasks through the orchestrator
        if (isComplexTask(trimmed) && extraParts.isEmpty()) {
            sendOrchestrated(trimmed)
            return
        }
        generationPipeline.execute(
            text = trimmed,
            extraParts = extraParts,
            buildConfig = ::buildGenerationConfig,
        )
    }

    fun sendOrchestrated(goal: String) {
        ensureSessionExists(goal.trim())
        engineScope.launch {
            val userMsg = UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text(goal.trim())),
            )
            _state.value = _state.value.copy(
                isProcessing = true,
                currentPhase = AgentPhase.PLANNING,
                messages = _state.value.messages + userMsg,
            )
            engineScope.launch { vibeEventBus.emit(VibeCodingEvent.GenerationStarted) }
            val config = buildGenerationConfig()
            val tools = config?.tools ?: emptyList()
            val result = orchestrator.execute(goal, tools, ::generateWithLLM)
            _state.value = _state.value.copy(
                isProcessing = false,
                currentPhase = result.phase,
                taskTree = result.taskTree,
                modifiedFiles = result.modifiedFiles,
            )
            if (result.success) {
                val msg = UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(UIMessagePart.Text(result.summary)),
                )
                _state.value = _state.value.copy(messages = _state.value.messages + msg)
            } else {
                val errorMsg = UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(UIMessagePart.Text("Failed: ${result.errors.joinToString(", ")}")),
                )
                _state.value = _state.value.copy(
                    messages = _state.value.messages + errorMsg,
                    error = result.errors.joinToString(", "),
                )
            }
            engineScope.launch { vibeEventBus.emit(VibeCodingEvent.GenerationFinished) }
            saveConversation()
        }
    }

    private suspend fun generateWithLLM(
        prompt: String,
        tools: List<Tool>,
        context: com.rk.ai.agent.context.ContextBundle,
    ): String {
        val config = buildGenerationConfig() ?: return ""
        val settings = config.settings
        val model = config.model
        val assistant = config.assistant
        val memories = config.memories
        val inputTransformers = config.inputTransformers
        val outputTransformers = config.outputTransformers
        val messages = listOf(
            UIMessage.user(prompt)
        )
        var resultText = ""
        generationHandler.generateText(
            settings = settings,
            model = model,
            messages = messages,
            assistant = assistant,
            memories = memories,
            tools = tools,
            inputTransformers = inputTransformers,
            outputTransformers = outputTransformers,
        ).collect { chunk ->
            when (chunk) {
                is GenerationChunk.Messages -> {
                    val lastMsg = chunk.messages.lastOrNull()
                    if (lastMsg != null) {
                        resultText = lastMsg.toText()
                    }
                }
                is GenerationChunk.GenerationError -> { }
                else -> { }
            }
        }
        return resultText
    }

    fun runAutonomous(goal: String) {
        sendOrchestrated(goal)
    }

    fun stopGeneration() {
        generationPipeline.cancel()
        orchestrator.stop()
        _state.value = _state.value.copy(isProcessing = false, currentPhase = AgentPhase.IDLE)
    }

    private fun isComplexTask(text: String): Boolean {
        val signals = listOf(
            "refactor", "implement", "create", "build", "fix",
            "add feature", "change", "update", "migrate",
            "write tests", "restructure", "complete", "full",
            "entire", "module", "feature", "rewrite", "convert",
            "optimize", "clean up", "add error handling",
            "add logging", "extract", "inline", "rename",
        )
        val lower = text.lowercase()
        val hasSignal = signals.any { lower.contains(it) }
        val isLong = text.length > 60
        val hasStepIndicators = listOf(
            "first", "then", "after", "finally", "step", "phase",
            "1.", "2.", "3.",
        ).any { lower.contains(it) }
        return hasSignal || isLong || hasStepIndicators
    }

    private fun ensureSessionExists(titleHint: String) {
        if (_state.value.activeSessionId == null) {
            val sessionId = Uuid.random()
            val node = SessionNode(id = sessionId, title = titleHint.take(80))
            _state.value = _state.value.copy(
                sessionTree = _state.value.sessionTree + node,
                activeSessionId = sessionId,
            )
        }
    }

    private suspend fun buildGenerationConfig(): GenerationConfig? {
        val settings = settingsStore.settingsFlow.value
        val model = settings.findModelById(settings.chatModelId)
        if (model == null) {
            _state.value = _state.value.copy(
                isProcessing = false,
                error = "No model selected. Configure a provider and model in VibeCoding settings.",
            )
            engineScope.launch { vibeEventBus.emit(VibeCodingEvent.GenerationError) }
            return null
        }

        val assistant = settings.getCurrentAssistant()
        val tools = buildToolList(assistant, settings)

        val memories = if (assistant.enableMemory) {
            val memoryAssistantId = if (assistant.useGlobalMemory) {
                MemoryRepository.GLOBAL_MEMORY_ID
            } else {
                assistant.id.toString()
            }
            memoryRepo.getMemoriesOfAssistant(memoryAssistantId)
        } else null

        val inputTransformers = listOfNotNull(
            systemPromptTransformer,
            PlaceholderTransformer,
            if (assistant.enableTimeReminder) TimeReminderTransformer else null,
            PromptInjectionTransformer,
        )

        val outputTransformers = listOfNotNull(
            ToolTagSanitizerTransformer,
            RegexOutputTransformer,
            Base64ImageToLocalFileTransformer.also { it.filesManager = filesManager },
        )

        return GenerationConfig(
            settings = settings,
            model = model,
            assistant = assistant,
            messages = _state.value.messages,
            tools = tools,
            memories = memories,
            inputTransformers = inputTransformers,
            outputTransformers = outputTransformers,
        )
    }

    fun clearConversation() {
        _state.value = VibeCodingState(
            commandCatalog = storedCommandCatalog.toList(),
            permissionAutoRespondRules = permissionManager.rules,
        )
        systemPromptTransformer.reset()
    }

    fun loadConversation(conversation: com.rk.ai.models.Conversation) {
        engineScope.launch(Dispatchers.IO) {
            try {
                val loaded = conversationRepo.getConversationById(conversation.id)
                if (loaded != null) {
                    val messages = loaded.currentMessages
                    _state.value = _state.value.copy(
                        messages = messages,
                        currentConversationId = loaded.id,
                        error = null,
                    )
                    saveCurrentSessionMessages()
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to load conversation: ${e.message}",
                )
            }
        }
    }

    private suspend fun saveConversation() {
        try {
            val messages = _state.value.messages
            if (messages.isEmpty()) return

            val existingId = _state.value.currentConversationId
            val convId = existingId ?: Uuid.random()
            val assistantId = getCurrentAssistantId()

            val title = messages.firstOrNull { it.role == MessageRole.USER }
                ?.toText()?.take(100)?.trim() ?: "VibeCoding"

            val conversation = com.rk.ai.models.Conversation(
                id = convId,
                assistantId = assistantId,
                title = title,
                messageNodes = messages.map { msg -> msg.toMessageNode() },
            )

            if (existingId != null && conversationRepo.existsConversationById(convId)) {
                conversationRepo.updateConversation(conversation)
            } else {
                conversationRepo.insertConversation(conversation)
            }

            _state.value = _state.value.copy(currentConversationId = convId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save conversation", e)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun approveTool(toolCallId: String) {
        if (isProcessing) return
        updateToolApproval(toolCallId, ToolApprovalState.Approved)
    }

    fun denyTool(toolCallId: String, reason: String = "") {
        if (isProcessing) return
        updateToolApproval(toolCallId, ToolApprovalState.Denied(reason))
    }

    fun answerTool(toolCallId: String, answer: String) {
        if (isProcessing) return
        updateToolApproval(toolCallId, ToolApprovalState.Answered(answer))
    }

    private fun updateToolApproval(toolCallId: String, newState: ToolApprovalState) {
        val messages = _state.value.messages.toMutableList()
        val lastIdx = messages.lastIndex
        if (lastIdx < 0) return
        val last = messages[lastIdx]
        val updatedParts = last.parts.map { part ->
            if (part is UIMessagePart.Tool && part.toolCallId == toolCallId) {
                part.copy(approvalState = newState)
            } else part
        }
        messages[lastIdx] = last.copy(parts = updatedParts)
        _state.value = _state.value.copy(messages = messages)
        saveCurrentSessionMessages()
        generationPipeline.resume(::buildGenerationConfig)
    }


}

private class VibeCodingFileManager(private val context: Context) : FileManager {
    override suspend fun saveUploadFromBytes(
        bytes: ByteArray,
        displayName: String,
        mimeType: String,
    ): String {
        val dir = File(context.filesDir, "vibecoding_mcp").also { it.mkdirs() }
        val file = File(dir, "${java.util.UUID.randomUUID()}_$displayName")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    override fun getFile(id: String): File = File(id)
}
