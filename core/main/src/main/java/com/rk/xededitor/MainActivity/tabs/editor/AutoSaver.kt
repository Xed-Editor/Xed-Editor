package com.rk.xededitor.MainActivity.tabs.editor

import com.rk.settings.Settings
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
                if (MainActivity.activityRef.get() == null || MainActivity.activityRef.get()?.isPaused == true) {
                    delay(1000)
                    continue
                }
                try {
                    // Get the auto-save delay from preferences or use the default
                    delay(Settings.auto_save_interval.toLong())

                    if (Settings.auto_save) {
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
    private suspend fun saveAllEditorFragments() = MainActivity.activityRef.get()?.apply {
        if (isDestroyed || isFinishing) {
            return@apply
        }

        if (tabViewModel.fragmentFiles.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                adapter?.tabFragments?.values?.forEach { weakRef ->
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
