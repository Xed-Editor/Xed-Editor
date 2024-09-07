package com.rk.xededitor.MainActivity.editor

import androidx.lifecycle.lifecycleScope
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.model.StaticData
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AutoSaver {

    var delayTime = 10000L
    private var job: Job? = null

    fun start(activity: MainActivity) {
        job?.let {
            if (it.isActive) {
                return
            }
        }

        job = activity.lifecycleScope.launch(Dispatchers.Default) {
            if (SettingsData.getBoolean(Keys.AUTO_SAVE, false)) {
                delayTime = SettingsData.getString(
                    Keys.AUTO_SAVE_TIME_VALUE,
                    delayTime.toString()
                ).toLong()

                var running = true
                while (running) {

                    delay(delayTime)

                    if (StaticData.fragments != null && StaticData.fragments.isNotEmpty() && !activity.isPaused && !activity.isFinishing && !activity.isDestroyed) {
                        FileManager.handleSaveAllFiles(activity, true)
                    } else {
                        running = false
                    }
                }
            }
        }
    }


    fun stop() {
        job?.let {
            if (it.isActive) {
                it.cancel()
                job = null
            }
        }
    }

}