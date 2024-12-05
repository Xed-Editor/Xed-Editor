package com.rk.xededitor

import android.app.Application
import android.content.Context
import com.rk.libcommons.SetupEditor
import com.rk.libcommons.application
import com.rk.resources.Res
import com.rk.settings.PreferencesData
import com.rk.xededitor.CrashHandler.CrashHandler
import com.rk.xededitor.MainActivity.tabs.editor.AutoSaver
import com.rk.xededitor.update.UpdateManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class App : Application() {

    companion object {
        @Deprecated("use libcommons application instead")
        lateinit var app: Application

        inline fun Context.getTempDir(): File {
            val tmp = File(filesDir.parentFile, "tmp")
            if (!tmp.exists()) {
                tmp.mkdir()
            }
            return tmp
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        app = this
        application = this
        Res.context = this

        super.onCreate()
        CrashHandler.INSTANCE.init(this)
        PreferencesData.initPref(this)

        GlobalScope.launch(Dispatchers.IO) {
            launch(Dispatchers.IO) {
                SetupEditor.init()
            }

            //delete useless file cache
            File(filesDir.parentFile, "shared_prefs/files.xml").apply {
                if (exists()) {
                    delete()
                }
            }

            AutoSaver.start()

            delay(6000)

            try {
                UpdateManager.fetch("dev")
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }


    }

    override fun onTerminate() {
        getTempDir().deleteRecursively()
        super.onTerminate()
    }
}
