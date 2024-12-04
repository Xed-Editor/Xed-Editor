package com.rk.xededitor.MainActivity.tabs.editor

import com.rk.libcommons.DefaultScope
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import kotlinx.coroutines.*

object AutoSaver {
    private const val DEFAULT_DELAY_MS = 10000L

    /**
     * Starts the auto-save process. Runs in a background coroutine, periodically checking
     * if auto-save is enabled and saving the open editor fragments.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        GlobalScope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    // Get the auto-save delay from preferences or use the default
                    val delayTime = PreferencesData.getString(
                        PreferencesKeys.AUTO_SAVE_TIME_VALUE,
                        DEFAULT_DELAY_MS.toString()
                    ).toLongOrNull() ?: DEFAULT_DELAY_MS

                    delay(delayTime)

                    if (PreferencesData.getBoolean(PreferencesKeys.AUTO_SAVE, false)) {
                        saveAllEditorFragments()
                    }
                } catch (e: Exception) {
                    println("Error in AutoSaver: ${e.message}")
                }
            }
        }
    }

    /**
     * Saves all editor fragments if conditions are met.
     */
    private suspend fun saveAllEditorFragments() {
        MainActivity.activityRef.get()?.let { activity ->
            if (activity.isFinishing || activity.isDestroyed) {
                println("Activity destroyed, unable to save files.")
                return
            }

            if (activity.tabViewModel.fragmentFiles.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    activity.adapter?.tabFragments?.values?.forEach { weakRef ->
                        weakRef.get()?.fragment?.let { fragment ->
                            if (fragment is EditorFragment) {
                                fragment.save(showToast = false, isAutoSaver = true)
                            }
                        }
                    }
                }
                println("Auto-save completed.")
            } else {
                println("No open fragments to save.")
            }
        } ?: println("MainActivity reference is null.")
    }
}
