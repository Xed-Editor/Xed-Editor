package com.rk.xededitor

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.rk.libcommons.SetupEditor
import com.rk.libcommons.application
import com.rk.libcommons.isAppInBackground
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
import java.lang.ref.WeakReference

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
        val appLifecycleTracker = AppLifecycleTracker()
        registerActivityLifecycleCallbacks(appLifecycleTracker)

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

    class AppLifecycleTracker : Application.ActivityLifecycleCallbacks {

        private var activityCount = 0

        fun isAppInForeground(): Boolean {
            return activityCount > 0
        }

        override fun onActivityResumed(activity: Activity) {
            activityCount++
            isAppInBackground = isAppInForeground()
        }

        override fun onActivityPaused(activity: Activity) {
            activityCount--
            isAppInBackground = isAppInForeground()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        }
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

}
