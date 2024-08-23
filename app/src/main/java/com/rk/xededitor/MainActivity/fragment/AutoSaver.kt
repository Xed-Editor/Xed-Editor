package com.rk.xededitor.MainActivity.fragment

import androidx.lifecycle.lifecycleScope
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.MenuClickHandler
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.Settings.SettingsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AutoSaver {

    var delayTime = 10000L
    private var job: Job? = null


    //todo
    //check if is in background before saving
    //do not save if content is empty

    fun start(activity: MainActivity) {
        job?.let {
            if (it.isActive) {
                return
            }
        }

        job = activity.lifecycleScope.launch(Dispatchers.Default) {
            if (SettingsData.getBoolean(SettingsData.Keys.AUTO_SAVE, false)) {
                delayTime =
                    SettingsData.getString(SettingsData.Keys.AUTO_SAVE_TIME_VALUE, delayTime.toString())
                        .toLong()
                var running = true
                while (running) {
                    delay(delayTime)
                    if (StaticData.fragments != null && StaticData.fragments.isNotEmpty() && !activity.isPaused && !activity.isFinishing && !activity.isDestroyed) {
                        MenuClickHandler.handleSaveAll(activity, true)
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