package com.rk.xededitor.MainActivity.tabs.editor

import com.rk.settings.Settings
import com.rk.settings.SettingsKey
import com.rk.xededitor.MainActivity.MainActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AutoSaver {
    private const val DEFAULT_DELAY_MS = 10000L

    /**
     * Starts the auto-save process. Runs in a background coroutine, periodically checking
     * if auto-save is enabled and saving the open editor fragments.
     * Auto-Saver survives activity lifecycles so do not put context here
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        GlobalScope.launch(Dispatchers.Default) {
            while (isActive) {
                if (MainActivity.activityRef.get()?.isPaused == true){
                    delay(100)
                    continue
                }
                try {
                    // Get the auto-save delay from preferences or use the default
                    val delayTime = Settings.getString(
                        SettingsKey.AUTO_SAVE_TIME_VALUE,
                        DEFAULT_DELAY_MS.toString()
                    ).toLongOrNull() ?: DEFAULT_DELAY_MS

                    delay(delayTime)

                    if (Settings.getBoolean(SettingsKey.AUTO_SAVE, false)) {
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
            if (activity.isDestroyed || activity.isFinishing) {
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
            }
        }
    }
}
