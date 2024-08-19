package com.rk.xededitor.MainActivity.fragment

import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.MenuClickHandler
import com.rk.xededitor.Settings.SettingsData
import kotlin.concurrent.thread

class AutoSaver {

    companion object {
        private var isRunning = false
        fun isRunning(): Boolean {
            return isRunning
        }

        private var thread: Thread? = null

        private var intervalMillis: Long = 2000

        fun setIntervalMillis(millis: Long) {
            intervalMillis = millis
        }

        fun stop() {
            thread?.interrupt()
        }
    }

    init {
        if (thread == null) {
            thread = Thread {
                if (!SettingsData.getBoolean(SettingsData.Keys.AUTO_SAVE,false)){ return@Thread }
                intervalMillis = SettingsData.getString(SettingsData.Keys.AUTO_SAVE_TIME_VALUE, "2000").toLong()
                isRunning = true
                while (!Thread.currentThread().isInterrupted) {
                    BaseActivity.getActivity(MainActivity::class.java)
                        ?.let { MenuClickHandler.handleSaveAll(it,true) }
                    Thread.sleep(intervalMillis)
                }
                isRunning = false

            }.also { it.start() }
        }

    }
}