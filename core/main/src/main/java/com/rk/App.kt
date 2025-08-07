package com.rk

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.system.Os
import com.github.anrwatchdog.ANRWatchDog
import com.rk.crashhandler.CrashHandler
import com.rk.extension.ExtensionManager
import com.rk.libcommons.application
import com.rk.libcommons.child
import com.rk.libcommons.editor.SetupEditor
import com.rk.libcommons.localBinDir
import com.rk.resources.Res
import com.rk.settings.Settings
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.editor.AutoSaver
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors

class App : Application() {

    companion object {
        fun getTempDir(): File {
            val tmp = File(application!!.cacheDir.parentFile, "tmp")
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

        Settings.visits = Settings.visits+1

        //wait until UpdateManager is done, it should only take few milliseconds
        UpdateManager.inspect()

        GlobalScope.launch {
            DocumentProvider.setDocumentProviderEnabled(this@App, Settings.expose_home_dir)

            getTempDir().apply {
                if (exists() && listFiles().isNullOrEmpty().not()) {
                    deleteRecursively()
                }
            }

            SetupEditor.init()
            AutoSaver.start()


            runCatching {
                val bridge = File(applicationInfo.nativeLibraryDir).child("libbridge.so")
                if (bridge.exists()){
                    Files.deleteIfExists(localBinDir().child("xed").toPath())
                    Os.symlink(bridge.absolutePath, localBinDir().child("xed").absolutePath)
                }
            }.onFailure {
                it.printStackTrace()
            }


            runCatching { UpdateChecker.checkForUpdates("dev") }



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
