@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.engine

import android.content.Context
import androidx.room.Room
import com.rk.ai.agent.AILoggingManager
import com.rk.ai.agent.GenerationChunk
import com.rk.ai.agent.GenerationHandler
import com.rk.ai.agent.transformers.InputMessageTransformer
import com.rk.ai.agent.transformers.TransformerContext
import com.rk.ai.core.AppScope
import com.rk.ai.models.UIMessage
import com.rk.ai.agent.tools.VibeCodingSystemTools
import com.rk.ai.agent.tools.VibeCodingToolRegistry
import com.rk.ai.persistence.db.AppDatabase
import com.rk.ai.persistence.db.fts.MessageFtsManager
import com.rk.ai.persistence.repo.ConversationRepository
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
import okhttp3.OkHttpClient
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
    json: Json = defaultJson,
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

    val providerManager = ProviderManager(okHttpClient, context)
    val settingsStore = SettingsStore(context, appScope)

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
            return listOf(UIMessage.system(VibeCodingSystemTools.SYSTEM_INSTRUCTIONS)) + messages
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

            runCatching {
                generationHandler.generateText(
                    settings = settings,
                    model = model,
                    messages = _state.value.messages,
                    assistant = settings.getCurrentAssistant(),
                    tools = toolRegistry.allTools,
                    inputTransformers = listOf(systemPromptTransformer),
                    outputTransformers = emptyList(),
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
}
