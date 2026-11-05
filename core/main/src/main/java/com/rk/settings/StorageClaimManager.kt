package com.rk.settings

import androidx.compose.ui.graphics.Color
import com.rk.utils.logError

class StorageClaimScope(val calculatedSizes: Map<StorageCategory, Long>, val totalSize: Long) {
    private val claims = mutableListOf<ClaimedStorage>()

    fun claim(category: StorageCategory, name: String, color: Color, size: Long) {
        claims.add(ClaimedStorage(name, color, size, category))
    }

    internal fun getClaims(): List<ClaimedStorage> = claims
}

data class ClaimedStorage(
    val name: String,
    val color: Color,
    val size: Long,
    val category: StorageCategory,
)

object StorageClaimManager {
    private val listeners = mutableListOf<StorageClaimScope.() -> Unit>()

    @Synchronized
    fun register(listener: StorageClaimScope.() -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    @Synchronized
    fun unregister(listener: StorageClaimScope.() -> Unit) {
        listeners.remove(listener)
    }

    @Synchronized
    fun getClaims(calculatedSizes: Map<StorageCategory, Long>, totalSize: Long): List<ClaimedStorage> {
        val scope = StorageClaimScope(calculatedSizes, totalSize)

        listeners.forEach { listener ->
            runCatching { scope.listener() }.onFailure { logError(it, "Failed to run claim") }
        }

        return scope.getClaims()
    }
}
