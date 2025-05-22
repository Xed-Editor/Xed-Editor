package com.rk

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
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.editor.AutoSaver
import com.rk.xededitor.ui.screens.settings.feature_toggles.InbuiltFeatures
import com.rk.xededitor.ui.screens.settings.mutators.Mutators
import kotlinx.coroutines.DelicateCoroutinesApi
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

        if (BuildConfig.DEBUG || Settings.anr_watchdog) {
            ANRWatchDog().start()
        }

        if (BuildConfig.DEBUG || Settings.strict_mode) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().apply {
                    detectAll()
                    penaltyLog()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
                            println(violation.message)
                            violation.printStackTrace()
                            violation.cause?.let { throw it }
                            println("vm policy error")
                        }
                    }
                }.build()
            )
        }

        //wait until UpdateManager is done, it should only take few milliseconds
        UpdateManager.inspect()

        GlobalScope.launch {
            AlpineDocumentProvider.setDocumentProviderEnabled(this@App, Settings.expose_home_dir)

            getTempDir().apply {
                if (exists() && listFiles().isNullOrEmpty().not()) {
                    deleteRecursively()
                }
            }

            SetupEditor.init()
            Mutators.loadMutators()
            AutoSaver.start()

            runCatching { UpdateChecker.checkForUpdates("dev") }

            if (InbuiltFeatures.extensions.state.value) {
                Extension.loadExtensions(this@App, GlobalScope)
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
