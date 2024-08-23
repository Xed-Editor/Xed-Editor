package com.rk.xededitor.MainActivity.fragment

import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.MenuClickHandler
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.Settings.SettingsData
import kotlin.concurrent.thread

class AutoSaver {

    companion object {

        fun isRunning(): Boolean {
            return thread != null
        }

        private var thread: Thread? = null

        private var intervalMillis: Long = 10000

        fun setIntervalMillis(millis: Long) {
            intervalMillis = millis
        }

        fun stop() {
            thread?.interrupt()
        }
    }

    //todo
    //check if is in background before saving
    //do not save if content is empty
    init {
        if (thread == null) {
            thread = Thread {
                var shouldRun = true
                if (!SettingsData.getBoolean(SettingsData.Keys.AUTO_SAVE,false)){ return@Thread }
                intervalMillis = SettingsData.getString(SettingsData.Keys.AUTO_SAVE_TIME_VALUE, intervalMillis.toString()).toLong()
                while (!Thread.currentThread().isInterrupted && shouldRun) {
                    BaseActivity.getActivity(MainActivity::class.java)
                        ?.let {
                            if (StaticData.fragments != null && StaticData.fragments.isNotEmpty()){
                                MenuClickHandler.handleSaveAll(it,true)
                            }else{
                                shouldRun = false
                            }
                        }
                    Thread.sleep(intervalMillis)
                }

                thread = null

            }.also { it.start() }
        }

    }
}