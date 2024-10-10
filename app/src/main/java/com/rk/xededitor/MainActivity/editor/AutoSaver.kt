package com.rk.xededitor.MainActivity.editor

import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AutoSaver {

    var delayTime = 10000L
    private var job: Job? = null

    @OptIn(DelicateCoroutinesApi::class)
    fun start(activity: MainActivity) {
        job?.let {
            if (it.isActive) {
                return
            }
        }

        job =
            GlobalScope.launch(Dispatchers.Default) {
                if (PreferencesData.getBoolean(PreferencesKeys.AUTO_SAVE, false)) {
                    delayTime =
                        PreferencesData.getString(
                                PreferencesKeys.AUTO_SAVE_TIME_VALUE,
                                delayTime.toString(),
                            )
                            .toLong()
                    while (true) {
                        delay(delayTime)
                        activity.let {
                            if (
                                it.tabViewModel.fragmentFiles.isNotEmpty() and
                                    it.isPaused.not() and
                                    it.isFinishing.not() and
                                    it.isDestroyed.not()
                            ) {
                                withContext(Dispatchers.Main) {
                                    it.adapter.tabFragments.values.forEach { f ->
                                        f?.get()?.save(false)
                                    }
                                }
                            }
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
