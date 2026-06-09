package com.rk.ai.nativeagent.engine

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class SessionNavigationState(
    val sessionTree: List<SessionNode> = emptyList(),
    val activeSessionId: Uuid? = null,
    val parentSessionId: Uuid? = null,
) {
    val sessionById: Map<Uuid, SessionNode> get() = sessionTree.associateBy { it.id }

    val currentSessionNode: SessionNode? get() {
        val id = activeSessionId ?: return null
        return sessionById[id]
    }

    val hasParentSession: Boolean get() = parentSessionId != null

    fun sessionLineage(sessionId: Uuid): List<Uuid> {
        val result = mutableListOf(sessionId)
        var current = sessionById[sessionId]
        while (current?.parentId != null) {
            result.add(current.parentId!!)
            current = sessionById[current.parentId]
        }
        return result
    }
}
