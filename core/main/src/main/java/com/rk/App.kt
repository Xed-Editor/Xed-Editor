package com.rk

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.system.Os
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.github.anrwatchdog.ANRWatchDog
import com.rk.crashhandler.CrashHandler
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.libcommons.application
import com.rk.libcommons.editor.FontCache
import com.rk.libcommons.editor.KarbonEditor
import com.rk.resources.Res
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.ui.activities.main.TabCache
import com.rk.xededitor.ui.theme.updateThemes
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(DelicateCoroutinesApi::class)
class App : Application() {

    companion object {
        fun getTempDir(): File {
            val tmp = File(application!!.cacheDir.parentFile, "tmp")
            if (!tmp.exists()) {
                tmp.mkdir()
            }
            return tmp
        }

        val isFDroid by lazy {
            val targetSdkVersion = application!!
                .applicationInfo
                .targetSdkVersion
            targetSdkVersion == 28
        }
    }

    init {
        application = this
        Res.application = this
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
        GlobalScope.launch(Dispatchers.IO) {
            TabCache.preloadTabs()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        val currentLocale = Locale.forLanguageTag(Settings.currentLang)
        val appLocale = LocaleListCompat.create(currentLocale)
        AppCompatDelegate.setApplicationLocales(appLocale)

        updateThemes()

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
                            violation.printStackTrace()
                            violation.cause?.let { throw it }
                        }
                    }
                }.build()
            )
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleListener())

        GlobalScope.launch {
            launch(Dispatchers.IO) {
                KarbonEditor.initGrammarRegistry()
            }
            launch {
                val fontPath = Settings.selected_font_path
                if (fontPath.isNotEmpty()) {
                    FontCache.loadFont(this@App, fontPath, Settings.is_selected_font_assest)
                } else {
                    FontCache.loadFont(this@App, "fonts/Default.ttf", true)
                }
            }

            if (Settings.restore_sessions){
                launch(Dispatchers.IO) {
                    Preference.preloadAllSettings()
                }
            }

            launch {
                DocumentProvider.setDocumentProviderEnabled(
                    this@App,
                    Settings.expose_home_dir
                )
            }

            launch(Dispatchers.IO) {
                getTempDir().apply {
                    if (exists() && listFiles().isNullOrEmpty().not()) {
                        deleteRecursively()
                    }
                }
            }

            launch {
                runCatching { UpdateChecker.checkForUpdates("dev") }
            }

            Settings.visits = Settings.visits + 1

            //wait until UpdateManager is done, it should only take few milliseconds
            UpdateManager.inspect()

        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
    }

}
