package com.rk.xededitor

import android.app.Application
import android.content.Context
import android.os.StrictMode
import com.github.anrwatchdog.ANRWatchDog
import com.rk.extension.Extension
import com.rk.extension.ExtensionManager
import com.rk.libcommons.application
import com.rk.libcommons.editor.SetupEditor
import com.rk.resources.Res
import com.rk.settings.Settings
import com.rk.settings.SettingsKey
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

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )

        ANRWatchDog().start()

        super.onCreate()

        CrashHandler.INSTANCE.init(this)
        Settings.initPref(this)

        GlobalScope.launch(Dispatchers.IO) {
            getTempDir().apply {
                if (exists() && listFiles().isNullOrEmpty().not()){
                    deleteRecursively()
                }
            }

            launch(Dispatchers.IO) { SetupEditor.init(GlobalScope) }
            Mutators.loadMutators()
            AutoSaver.start()

            runCatching { UpdateManager.fetch("dev") }
            delay(500)
            if (Settings.getBoolean(SettingsKey.ENABLE_EXTENSIONS,false)){
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
