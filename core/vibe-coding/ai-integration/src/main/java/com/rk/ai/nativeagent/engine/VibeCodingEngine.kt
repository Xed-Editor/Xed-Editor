@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.engine

import android.content.Context
import androidx.room.Room
import com.rk.ai.agent.AILoggingManager
import com.rk.ai.agent.AppEventBus
import com.rk.ai.agent.GenerationChunk
import com.rk.ai.agent.GenerationHandler
import com.rk.ai.agent.files.FilesManager
import com.rk.ai.agent.files.SkillManager
import com.rk.ai.agent.tools.LocalTools
import com.rk.ai.agent.tools.VibeCodingSystemTools
import com.rk.ai.agent.tools.VibeCodingToolRegistry
import com.rk.ai.agent.tools.createSearchTools
import com.rk.ai.agent.tools.createSkillTools
import com.rk.ai.agent.transformers.Base64ImageToLocalFileTransformer
import com.rk.ai.agent.transformers.InputMessageTransformer
import com.rk.ai.agent.transformers.PlaceholderTransformer
import com.rk.ai.agent.transformers.PromptInjectionTransformer
import com.rk.ai.agent.transformers.RegexOutputTransformer
import com.rk.ai.agent.transformers.TimeReminderTransformer
import com.rk.ai.agent.transformers.TransformerContext
import com.rk.ai.core.AppScope
import com.rk.ai.mcp.FileManager
import com.rk.ai.mcp.McpManager
import com.rk.ai.models.McpTool
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessage
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
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.uuid.ExperimentalUuidApi

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

class VibeCodingEngine(
    private val context: Context,
    private val ideService: IdeService,
    scope: CoroutineScope? = null,
    private val json: Json = defaultJson,
    okHttpClient: OkHttpClient = buildOkHttpClient(),
) {
    private val engineScope: CoroutineScope =
        scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appScope = AppScope()

    private val database = buildDatabase(context)
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
    private val skillManager = SkillManager(context, settingsStore)
    private val localTools = LocalTools(context, eventBus)

    val generationHandler = GenerationHandler(
        context = context,
        providerManager = providerManager,
        json = json,
        memoryRepo = memoryRepo,
        conversationRepo = conversationRepo,
        aiLoggingManager = AILoggingManager(),
    )

    val toolRegistry = VibeCodingToolRegistry(ideService, context)

    private var currentJob: Job? = null

    private inner class SystemPromptTransformer : InputMessageTransformer {
        private var injected = false

        override suspend fun transform(
            ctx: TransformerContext,
            messages: List<UIMessage>,
        ): List<UIMessage> {
            if (injected || messages.isEmpty()) return messages
            injected = true

            val workspaceContext = buildString {
                appendLine(VibeCodingSystemTools.SYSTEM_INSTRUCTIONS)
                appendLine()
                appendLine("## Current Workspace Context")
                appendLine()

                try {
                    val workspacePath = ideService.getPrimaryWorkspacePath()
                    appendLine("Workspace: $workspacePath")
                    appendLine()

                    val projectConfig = ideService.getProjectConfig(workspacePath)
                    val language = projectConfig["language"]?.asString
                    val buildSystem = projectConfig["buildSystem"]?.asString
                    if (language != null) appendLine("Language: $language")
                    if (buildSystem != null) appendLine("Build System: $buildSystem")

                    appendLine()
                    val gitStatus = ideService.getGitStatus(workspacePath)
                    val branch = gitStatus["branch"]?.asString ?: "unknown"
                    val changes = gitStatus["changes"]?.asJsonArray?.size() ?: 0
                    appendLine("Git Branch: $branch ($changes uncommitted changes)")
                    appendLine()

                    val openFiles = ideService.getOpenFiles()
                    if (openFiles.isNotEmpty()) {
                        appendLine("Open Files:")
                        openFiles.forEach { f ->
                            val path = f["path"]?.asString ?: f["filePath"]?.asString ?: ""
                            appendLine("  - $path")
                        }
                        appendLine()
                    }

                    val activeFile = ideService.getActiveFile()
                    if (activeFile != null) {
                        val activePath = activeFile["path"]?.asString ?: activeFile["filePath"]?.asString ?: ""
                        appendLine("Active File: $activePath")
                        appendLine()
                    }
                } catch (e: Exception) {
                    appendLine("(Workspace context unavailable: ${e.message})")
                }

                appendLine("Use the available tools to read files, search code, and make changes.")
            }

            return listOf(UIMessage.system(workspaceContext.toString())) + messages
        }

        fun reset() { injected = false }
    }

    private val systemPromptTransformer = SystemPromptTransformer()

    private val _state = MutableStateFlow(VibeCodingState())
    val state: StateFlow<VibeCodingState> = _state.asStateFlow()

    val messages: List<UIMessage> get() = _state.value.messages
    val isProcessing: Boolean get() = _state.value.isProcessing

    fun sendMessage(text: String) {
        currentJob?.cancel()
        currentJob = engineScope.launch(Dispatchers.IO) {
            val trimmed = text.trim()
            _state.value = _state.value.copy(isProcessing = true, error = null)
            _state.value = _state.value.copy(
                messages = _state.value.messages + UIMessage.user(trimmed),
            )

            val settings = settingsStore.settingsFlow.value
            val model = settings.findModelById(settings.chatModelId)
            if (model == null) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    error = "No model selected. Configure a provider and model in VibeCoding settings.",
                )
                return@launch
            }

            val assistant = settings.getCurrentAssistant()

            val enhancedTools = buildList {
                addAll(toolRegistry.allTools)

                addAll(localTools.getTools(assistant.localTools))

                if (settings.enableWebSearch) {
                    addAll(createSearchTools(settings))
                }

                mcpManager.getAllAvailableTools().forEach { (serverId, mcpTool) ->
                    add(Tool(
                        name = mcpTool.name,
                        description = mcpTool.description ?: "",
                        parameters = mcpTool.inputSchema?.let { schema ->
                            { schema }
                        } ?: { com.rk.ai.models.InputSchema.Obj(kotlinx.serialization.json.buildJsonObject { }) },
                        execute = { args ->
                            val kotlinxArgs = json.parseToJsonElement(args.toString()).jsonObject
                            mcpManager.callTool(serverId, mcpTool.name, kotlinxArgs)
                        },
                    ))
                }

                addAll(createSkillTools(
                    enabledSkills = assistant.enabledSkills,
                    allSkills = skillManager.listSkills(),
                    skillManager = skillManager,
                ))
            }

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
                RegexOutputTransformer,
                Base64ImageToLocalFileTransformer.also { it.filesManager = filesManager },
            )

            runCatching {
                generationHandler.generateText(
                    settings = settings,
                    model = model,
                    messages = _state.value.messages,
                    assistant = assistant,
                    memories = memories,
                    tools = enhancedTools,
                    inputTransformers = inputTransformers,
                    outputTransformers = outputTransformers,
                ).collect { chunk ->
                    when (chunk) {
                        is GenerationChunk.Messages ->
                            _state.value = _state.value.copy(messages = chunk.messages)
                        else -> {}
                    }
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isProcessing = false,
                    error = e.message ?: "Generation failed",
                )
                return@launch
            }

            _state.value = _state.value.copy(isProcessing = false)
        }
    }

    fun stopGeneration() {
        currentJob?.cancel()
        currentJob = null
        _state.value = _state.value.copy(isProcessing = false)
    }

    fun clearConversation() {
        _state.value = VibeCodingState()
        systemPromptTransformer.reset()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun approveTool(toolCallId: String) {
        val messages = _state.value.messages.toMutableList()
        val lastIdx = messages.lastIndex
        if (lastIdx < 0) return
        val last = messages[lastIdx]
        val updatedParts = last.parts.map { part ->
            if (part is com.rk.ai.models.UIMessagePart.Tool && part.toolCallId == toolCallId) {
                part.copy(approvalState = com.rk.ai.models.ToolApprovalState.Approved)
            } else part
        }
        messages[lastIdx] = last.copy(parts = updatedParts)
        _state.value = _state.value.copy(messages = messages)
        resumeGeneration()
    }

    fun denyTool(toolCallId: String, reason: String) {
        val messages = _state.value.messages.toMutableList()
        val lastIdx = messages.lastIndex
        if (lastIdx < 0) return
        val last = messages[lastIdx]
        val updatedParts = last.parts.map { part ->
            if (part is com.rk.ai.models.UIMessagePart.Tool && part.toolCallId == toolCallId) {
                part.copy(approvalState = com.rk.ai.models.ToolApprovalState.Denied(reason))
            } else part
        }
        messages[lastIdx] = last.copy(parts = updatedParts)
        _state.value = _state.value.copy(messages = messages)
        resumeGeneration()
    }

    fun answerTool(toolCallId: String, answer: String) {
        val messages = _state.value.messages.toMutableList()
        val lastIdx = messages.lastIndex
        if (lastIdx < 0) return
        val last = messages[lastIdx]
        val updatedParts = last.parts.map { part ->
            if (part is com.rk.ai.models.UIMessagePart.Tool && part.toolCallId == toolCallId) {
                part.copy(approvalState = com.rk.ai.models.ToolApprovalState.Answered(answer))
            } else part
        }
        messages[lastIdx] = last.copy(parts = updatedParts)
        _state.value = _state.value.copy(messages = messages)
        resumeGeneration()
    }

    private fun resumeGeneration() {
        currentJob?.cancel()
        currentJob = engineScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isProcessing = true, error = null)
            val settings = settingsStore.settingsFlow.value
            val model = settings.findModelById(settings.chatModelId)
            val assistant = settings.getCurrentAssistant()
            if (model == null || assistant == null) {
                _state.value = _state.value.copy(isProcessing = false, error = "No model or assistant configured")
                return@launch
            }

            val memories = if (assistant.enableMemory) {
                val memoryAssistantId = if (assistant.useGlobalMemory) {
                    com.rk.ai.persistence.repo.MemoryRepository.GLOBAL_MEMORY_ID
                } else {
                    assistant.id.toString()
                }
                memoryRepo.getMemoriesOfAssistant(memoryAssistantId)
            } else null

            runCatching {
                generationHandler.generateText(
                    settings = settings,
                    model = model,
                    messages = _state.value.messages,
                    assistant = assistant,
                    memories = memories,
                    tools = buildList {
                        addAll(toolRegistry.allTools)
                        addAll(localTools.getTools(assistant.localTools))
                        if (settings.enableWebSearch) addAll(createSearchTools(settings))
                        addAll(createSkillTools(enabledSkills = assistant.enabledSkills, allSkills = skillManager.listSkills(), skillManager = skillManager))
                    },
                    inputTransformers = emptyList(),
                    outputTransformers = emptyList(),
                ).collect { chunk ->
                    when (chunk) {
                        is GenerationChunk.Messages ->
                            _state.value = _state.value.copy(messages = chunk.messages)
                    }
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(isProcessing = false, error = e.message)
            }
            _state.value = _state.value.copy(isProcessing = false)
        }
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
