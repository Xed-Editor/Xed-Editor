package com.rk.xededitor

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.color.MaterialColors
import com.rk.extension.Extension
import com.rk.extension.ExtensionManager
import com.rk.libcommons.editor.SetupEditor
import com.rk.libcommons.application
import com.rk.libcommons.currentActivity
import com.rk.resources.Res
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.CrashHandler.CrashHandler
import com.rk.xededitor.MainActivity.tabs.editor.AutoSaver
import com.rk.xededitor.ui.screens.settings.mutators.Mutators
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
        fun Context.getTempDir(): File {
            val tmp = File(filesDir.parentFile, "tmp")
            if (!tmp.exists()) {
                tmp.mkdir()
            }
            return tmp
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        application = this
        Res.context = this
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                currentActivity = WeakReference(activity)
                GlobalScope.launch(Dispatchers.IO) {

                    SetupEditor.initActivity(activity, calculateColors = {
                        val lightThemeContext = ContextThemeWrapper(
                            activity,
                            com.google.android.material.R.style.Theme_Material3_DynamicColors_Light
                        )
                        val darkThemeContext = ContextThemeWrapper(
                            activity,
                            com.google.android.material.R.style.Theme_Material3_DynamicColors_Dark
                        )

                        val lightSurfaceColor = MaterialColors.getColor(
                            lightThemeContext,
                            com.google.android.material.R.attr.colorSurface,
                            Color.WHITE
                        )

                        val darkSurfaceColor = MaterialColors.getColor(
                            darkThemeContext,
                            com.google.android.material.R.attr.colorSurface,
                            Color.BLACK
                        )

                        val lightsurfaceColorHex =
                            String.format("#%06X", 0xFFFFFF and lightSurfaceColor)
                        val darksurfaceColorHex =
                            String.format("#%06X", 0xFFFFFF and darkSurfaceColor)

                        Pair(darksurfaceColorHex, lightsurfaceColorHex)
                    })
                }
            }

            override fun onActivityStarted(activity: Activity) {

            }

            override fun onActivityResumed(activity: Activity) {

            }

            override fun onActivityPaused(activity: Activity) {

            }

            override fun onActivityStopped(activity: Activity) {

            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

            }

            override fun onActivityDestroyed(activity: Activity) {

            }
        })

        CrashHandler.INSTANCE.init(this)
        PreferencesData.initPref(this)

        GlobalScope.launch(Dispatchers.IO) {
            launch(Dispatchers.IO) {
                SetupEditor.init(GlobalScope)
            }

            Mutators.loadMutators()

            //delete useless file cache
            File(filesDir.parentFile, "shared_prefs/files.xml").apply {
                if (exists()) {
                    delete()
                }
            }

            AutoSaver.start()

            runCatching {
                UpdateManager.fetch("dev")
            }

            delay(500)
            if (PreferencesData.getBoolean(PreferencesKeys.ENABLE_EXTENSIONS,false)){
                Extension.executeExtensions(this@App,GlobalScope)
                ExtensionManager.onAppLaunched()
            }

        }

    }

    override fun onTerminate() {
        getTempDir().deleteRecursively()
        super.onTerminate()
    }

    override fun onLowMemory() {
        ExtensionManager.onLowMemory()
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        ExtensionManager.onLowMemory()
        super.onTrimMemory(level)
    }

}
