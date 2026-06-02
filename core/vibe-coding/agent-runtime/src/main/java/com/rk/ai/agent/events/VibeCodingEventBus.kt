@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.agent.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed class VibeCodingEvent {
    data class MessageAdded(
        val sessionId: Uuid,
        val messageId: Uuid,
    ) : VibeCodingEvent()

    data class MessageUpdated(
        val sessionId: Uuid,
        val messageId: Uuid,
    ) : VibeCodingEvent()

    data class SessionCreated(
        val sessionId: Uuid,
        val parentSessionId: Uuid? = null,
    ) : VibeCodingEvent()

    data class ToolPendingApproval(
        val sessionId: Uuid,
        val toolCallId: String,
        val toolName: String,
        val reason: String? = null,
    ) : VibeCodingEvent()

    data class ToolExecuted(
        val sessionId: Uuid,
        val toolName: String,
        val success: Boolean,
    ) : VibeCodingEvent()

    data class PermissionAsked(
        val sessionId: Uuid,
        val permissionId: String,
        val permissionType: String,
        val patterns: List<String>,
    ) : VibeCodingEvent()

    data class SecurityAlert(
        val severity: String,
        val message: String,
        val toolName: String? = null,
        val filePath: String? = null,
    ) : VibeCodingEvent()

    data object GenerationStarted : VibeCodingEvent()
    data object GenerationFinished : VibeCodingEvent()
    data object GenerationError : VibeCodingEvent()

    data class CommandExecuted(
        val command: String,
        val success: Boolean,
    ) : VibeCodingEvent()

    data class TodoUpdated(
        val sessionId: Uuid,
        val todos: List<SessionTodo>,
    ) : VibeCodingEvent()
}

data class SessionTodo(
    val id: String,
    val description: String,
    val status: SessionTodoStatus = SessionTodoStatus.PENDING,
)

enum class SessionTodoStatus { PENDING, COMPLETED, CANCELLED }

class VibeCodingEventBus {
    private val mutex = Mutex()
    private val _events = MutableSharedFlow<VibeCodingEvent>(
        replay = 0,
        extraBufferCapacity = 256,
    )
    val events: SharedFlow<VibeCodingEvent> = _events.asSharedFlow()

    private val coalesceMap = mutableMapOf<String, VibeCodingEvent>()
    private var coalesceTimer: kotlinx.coroutines.Job? = null

    suspend fun emit(event: VibeCodingEvent) {
        _events.emit(event)
    }

    fun emitBlocking(event: VibeCodingEvent) {
        kotlinx.coroutines.runBlocking { _events.emit(event) }
    }

    suspend fun emitCoalesced(key: String, event: VibeCodingEvent) {
        mutex.withLock {
            coalesceMap[key] = event
        }
    }

    suspend fun flushCoalesced() {
        mutex.withLock {
            val batch = coalesceMap.values.toList()
            coalesceMap.clear()
            for (event in batch) {
                _events.emit(event)
            }
        }
    }
}
