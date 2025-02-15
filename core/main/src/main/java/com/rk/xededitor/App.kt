package com.rk.xededitor

import android.app.Application
import android.os.Build
import android.os.StrictMode
import com.github.anrwatchdog.ANRWatchDog
import com.rk.crashhandler.CrashHandler
import com.rk.extension.Extension
import com.rk.extension.ExtensionManager
import com.rk.libcommons.application
import com.rk.libcommons.editor.SetupEditor
import com.rk.resources.Res
import com.rk.settings.Settings
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.editor.AutoSaver
import com.rk.xededitor.ui.screens.settings.mutators.Mutators
import com.rk.xededitor.update.UpdateChecker
import com.rk.xededitor.update.UpdateManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors


class App : Application() {

    companion object {
        fun getTempDir(): File {
            val tmp = File(application!!.filesDir.parentFile, "tmp")
            if (!tmp.exists()) {
                tmp.mkdir()
            }
            return tmp
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        application = this
        Res.application = this

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
        ANRWatchDog().start()

        if (BuildConfig.DEBUG){
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().apply {
                    detectAll()
                    penaltyLog()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
                        penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
                            println(violation.message)
                            violation.printStackTrace()
                            violation.cause?.let { throw it }
                        }
                    }
                }.build()
            )
        }

        //wait until UpdateManager is done, it should only take few milliseconds
        UpdateManager.inspect()

        GlobalScope.launch(Dispatchers.IO) {
            getTempDir().apply {
                if (exists() && listFiles().isNullOrEmpty().not()){ deleteRecursively() }
            }

            launch(Dispatchers.IO) { SetupEditor.init(GlobalScope) }
            Mutators.loadMutators()
            AutoSaver.start()

            runCatching { UpdateChecker.checkForUpdates("dev") }

            if (Settings.enable_extensions){
                Extension.executeExtensions(this@App,GlobalScope)
                ExtensionManager.onAppLaunched()
            }
        }

    }

    override fun onTrimMemory(level: Int) {
        MainActivity.withContext {
            binding?.viewpager2?.offscreenPageLimit = 1
        }
        ExtensionManager.onLowMemory()
        super.onTrimMemory(level)
    }

}
