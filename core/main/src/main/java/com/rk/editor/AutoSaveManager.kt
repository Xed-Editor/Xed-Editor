package com.rk.editor

import android.util.Log
import com.rk.settings.Settings
import java.io.File
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AutoSaveManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var autoSaveJob: Job? = null
    private val dirtyFiles = LinkedHashSet<AutoSaveTarget>()

    data class AutoSaveTarget(
        val file: File,
        val getContent: suspend () -> String?,
        val onSaved: suspend (Boolean) -> Unit = {},
    )

    private val activeTargets = WeakHashMap<File, AutoSaveTarget>()

    fun start() {
        stop()
        autoSaveJob = scope.launch {
            while (isActive) {
                val intervalMs = if (Settings.auto_save) Settings.auto_save_delay else 30_000L
                delay(intervalMs)
                if (!Settings.auto_save) continue
                flushAll()
            }
        }
    }

    fun stop() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    fun markDirty(target: AutoSaveTarget) {
        synchronized(dirtyFiles) {
            dirtyFiles.add(target)
        }
        activeTargets[target.file] = target
    }

    fun markClean(file: File) {
        synchronized(dirtyFiles) {
            dirtyFiles.removeAll { it.file == file }
        }
    }

    suspend fun flushAll() {
        val toSave: List<AutoSaveTarget>
        synchronized(dirtyFiles) {
            toSave = dirtyFiles.toList()
            dirtyFiles.clear()
        }
        for (target in toSave) {
            saveFile(target)
        }
    }

    suspend fun flushFile(file: File) {
        val target: AutoSaveTarget?
        synchronized(dirtyFiles) {
            target = dirtyFiles.find { it.file == file }
            if (target != null) dirtyFiles.remove(target)
        }
        if (target != null) saveFile(target)
    }

    private suspend fun saveFile(target: AutoSaveTarget) {
        try {
            val content = target.getContent()
            if (content == null) {
                target.onSaved(false)
                return
            }
            val tmpFile = File(target.file.parentFile, ".${target.file.name}.tmp")
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                tmpFile.parentFile?.mkdirs()
                tmpFile.writeText(content, Charsets.UTF_8)
                val success = tmpFile.renameTo(target.file)
                if (!success) {
                    target.file.writeText(content, Charsets.UTF_8)
                }
            }
            target.onSaved(true)
            Log.d("AutoSave", "Saved ${target.file.name}")
        } catch (e: Exception) {
            Log.e("AutoSave", "Failed to save ${target.file.name}", e)
            synchronized(dirtyFiles) { dirtyFiles.add(target) }
            target.onSaved(false)
        }
    }

    fun hasUnsavedChanges(): Boolean {
        synchronized(dirtyFiles) { return dirtyFiles.isNotEmpty() }
    }

    fun shutdown() {
        scope.launch { flushAll() }.invokeOnCompletion { scope.cancel() }
    }
}
