package com.rk.core.performance

import android.os.Debug
import android.util.Log
import com.rk.xededitor.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentLinkedQueue

object MemoryGuard {
    private const val TAG = "MemoryGuard"
    private const val WARNING_THRESHOLD_MB = 200
    private const val CRITICAL_THRESHOLD_MB = 100
    private const val CHECK_INTERVAL_MS = 30000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val leakCandidates = ConcurrentLinkedQueue<() -> Unit>()
    private val memoryListeners = mutableListOf<(MemoryEvent) -> Unit>()

    @Volatile var isMonitoring = false
        private set

    @Volatile var lastFreeMemoryMB: Long = 0
        private set

    @Volatile var lastTotalMemoryMB: Long = 0
        private set

    sealed class MemoryEvent {
        data class Warning(val freeMB: Long) : MemoryEvent()
        data class Critical(val freeMB: Long) : MemoryEvent()
        data class Normal(val freeMB: Long) : MemoryEvent()
        data class GcPerformed(val freedMB: Long) : MemoryEvent()
    }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        if (!BuildConfig.DEBUG) return

        scope.launch {
            while (isActive && isMonitoring) {
                checkMemory()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun stopMonitoring() {
        isMonitoring = false
    }

    fun registerOnLowMemory(releaseFn: () -> Unit) {
        leakCandidates.add(releaseFn)
    }

    fun unregisterOnLowMemory(releaseFn: () -> Unit) {
        leakCandidates.remove(releaseFn)
    }

    fun onMemoryEvent(listener: (MemoryEvent) -> Unit) {
        memoryListeners.add(listener)
    }

    fun removeMemoryListener(listener: (MemoryEvent) -> Unit) {
        memoryListeners.remove(listener)
    }

    fun requestGc(): Long {
        val before = freeMemoryMB()
        Runtime.getRuntime().gc()
        System.gc()
        val after = freeMemoryMB()
        val freed = after - before
        if (freed > 0) {
            Log.d(TAG, "GC freed ${freed}MB")
            notifyListeners(MemoryEvent.GcPerformed(freed))
        }
        return freed
    }

    fun freeMemoryMB(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.freeMemory().toFloat() / (1024 * 1024)).toLong()
    }

    fun totalMemoryMB(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory().toFloat() / (1024 * 1024)).toLong()
    }

    fun maxMemoryMB(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.maxMemory().toFloat() / (1024 * 1024)).toLong()
    }

    fun memorySnapshot(): String = buildString {
        appendLine("Memory Snapshot:")
        appendLine("  Free: ${freeMemoryMB()}MB")
        appendLine("  Total: ${totalMemoryMB()}MB")
        appendLine("  Max: ${maxMemoryMB()}MB")
        appendLine("  Used: ${maxMemoryMB() - freeMemoryMB()}MB / ${maxMemoryMB()}MB")
        appendLine("  Native Heap: ${Debug.getNativeHeapSize() / (1024 * 1024)}MB")
        appendLine("  Native Free: ${Debug.getNativeHeapFreeSize() / (1024 * 1024)}MB")
        appendLine("  Native Alloc: ${Debug.getNativeHeapAllocatedSize() / (1024 * 1024)}MB")
    }

    private fun checkMemory() {
        val free = freeMemoryMB()
        val total = totalMemoryMB()
        lastFreeMemoryMB = free
        lastTotalMemoryMB = total

        if (free < CRITICAL_THRESHOLD_MB) {
            Log.w(TAG, "Critical memory: ${free}MB free")
            notifyListeners(MemoryEvent.Critical(free))
            releaseMemory()
            requestGc()
        } else if (free < WARNING_THRESHOLD_MB) {
            Log.w(TAG, "Low memory: ${free}MB free")
            notifyListeners(MemoryEvent.Warning(free))
        } else {
            if (BuildConfig.DEBUG) Log.d(TAG, "Memory OK: ${free}MB free")
        }
    }

    private fun releaseMemory() {
        var released = 0
        leakCandidates.forEach { releaseFn ->
            try {
                releaseFn()
                released++
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing memory", e)
            }
        }
        Log.d(TAG, "Released $released memory handlers")
    }

    private fun notifyListeners(event: MemoryEvent) {
        memoryListeners.forEach { it(event) }
    }
}

object ComposePerformanceOptimizer {
    private val TAG = "ComposePerf"

    @Volatile var skipInvalidation = false
        private set

    @Volatile var frameDropCount = 0
        private set

    @Volatile var lastFrameTimeMs = 0L
        private set

    private val frameTimes = ArrayDeque<Long>()
    private const val FRAME_HISTORY_SIZE = 60
    private const val DROP_THRESHOLD_MS = 32L // ~30fps

    fun recordFrameTime(frameTimeMs: Long) {
        lastFrameTimeMs = frameTimeMs
        frameTimes.addLast(frameTimeMs)
        if (frameTimes.size > FRAME_HISTORY_SIZE) {
            frameTimes.removeFirst()
        }
        if (frameTimeMs > DROP_THRESHOLD_MS) {
            frameDropCount++
        }
    }

    fun averageFrameTimeMs(): Double {
        if (frameTimes.isEmpty()) return 0.0
        return frameTimes.average()
    }

    fun frameDropRate(): Double {
        if (frameTimes.isEmpty()) return 0.0
        return frameTimes.count { it > DROP_THRESHOLD_MS }.toDouble() / frameTimes.size
    }

    fun optimizeForLargeContent() {
        skipInvalidation = true
    }

    fun restoreNormalMode() {
        skipInvalidation = false
    }

    fun reset() {
        frameTimes.clear()
        frameDropCount = 0
        skipInvalidation = false
    }
}

object StringOptimizer {
    private val TAG = "StringOptimizer"

    private val stringBuilderPool = ThreadLocal<MutableList<StringBuilder>>()

    fun borrowBuilder(): StringBuilder {
        val pool = stringBuilderPool.get() ?: run {
            val p = mutableListOf<StringBuilder>()
            stringBuilderPool.set(p)
            p
        }
        return if (pool.isNotEmpty()) pool.removeAt(pool.lastIndex).also { it.clear() }
        else StringBuilder(256)
    }

    fun returnBuilder(builder: StringBuilder) {
        builder.clear()
        val pool = stringBuilderPool.get() ?: return
        if (pool.size < 8) pool.add(builder)
    }

    fun <T> buildStringOptimized(block: StringBuilder.() -> T): String {
        val sb = borrowBuilder()
        try {
            sb.block()
            return sb.toString()
        } finally {
            returnBuilder(sb)
        }
    }
}
