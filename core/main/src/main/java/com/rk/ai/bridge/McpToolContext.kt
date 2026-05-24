package com.rk.ai.bridge

import com.rk.ai.service.IdeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class McpToolContext(
    val ideService: IdeService,
    val scope: CoroutineScope,
    val timeoutMs: Long = 60_000L,
    val maxOutputSize: Long = 5_242_880L,
) {
    private val _progress = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val progress: Flow<String> = _progress.asSharedFlow()

    suspend fun sendProgress(message: String) {
        _progress.emit(message)
    }

    fun trySendProgress(message: String) {
        _progress.tryEmit(message)
    }
}
