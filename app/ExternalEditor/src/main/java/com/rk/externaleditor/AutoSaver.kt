package com.rk.externaleditor

import android.app.Activity
import androidx.lifecycle.lifecycleScope
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoSaver(activity: SimpleEditor) {
    private val DEFAULT_DELAY_MS = 10000L

    init {
        activity.lifecycleScope.launch {
            while (isActive) {
                try {
                    // Get the auto-save delay from preferences or use the default
                    val delayTime = PreferencesData.getString(
                        PreferencesKeys.AUTO_SAVE_TIME_VALUE,
                        DEFAULT_DELAY_MS.toString()
                    ).toLongOrNull() ?: DEFAULT_DELAY_MS

                    delay(delayTime)

                    if (PreferencesData.getBoolean(PreferencesKeys.AUTO_SAVE, false)) {
                        activity.save()
                    }
                } catch (e: Exception) {
                    println("Error in AutoSaver: ${e.message}")
                }
            }
        }
    }
}