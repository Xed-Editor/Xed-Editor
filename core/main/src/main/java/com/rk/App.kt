package com.rk

import android.app.Application
import android.os.Build
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.github.anrwatchdog.ANRWatchDog
import com.rk.activities.main.SessionManager
import com.rk.crashhandler.CrashHandler
import com.rk.editor.Editor
import com.rk.editor.FontCache
import com.rk.extension.ExtensionAPIManager
import com.rk.extension.ExtensionManager
import com.rk.extension.loadAllExtensions
import com.rk.lsp.LspPersistence
import com.rk.lsp.MarkdownImageProvider
import com.rk.resources.Res
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.settings.debugOptions.startThemeFlipperIfNotRunning
import com.rk.theme.updateThemes
import com.rk.utils.application
import com.rk.utils.getTempDir
import com.rk.xededitor.BuildConfig
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
class App : Application() {
    companion object {
        private var _extensionManager: ExtensionManager? = null
        val extensionManager: ExtensionManager
            get() {
                if (_extensionManager == null) {
                    _extensionManager = ExtensionManager(application!!)
                }

                return _extensionManager!!
            }
    }

    init {
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        application = this
        Res.application = this

        updateThemes()
        LspPersistence.restoreServers()
        MarkdownImageProvider.register()

        val currentLocale = Locale.forLanguageTag(Settings.current_lang)
        val appLocale = LocaleListCompat.create(currentLocale)
        AppCompatDelegate.setApplicationLocales(appLocale)

        GlobalScope.launch(Dispatchers.IO) {
            launch(Dispatchers.IO) {
                extensionManager.indexLocalExtensions()
                extensionManager.loadAllExtensions()
                registerActivityLifecycleCallbacks(ExtensionAPIManager)
            }

            launch { Editor.initGrammarRegistry() }

            launch(Dispatchers.IO) { SessionManager.preloadSession() }

            launch(Dispatchers.IO) {
                val fontPath = Settings.selected_font_path
                if (fontPath.isNotEmpty()) {
                    FontCache.loadFont(this@App, fontPath, Settings.is_selected_font_asset)
                } else {
                    FontCache.loadFont(this@App, "fonts/Default.ttf", true)
                }
            }

            launch(Dispatchers.IO) { Preference.preloadAllSettings() }

            launch { DocumentProvider.setDocumentProviderEnabled(this@App, Settings.expose_home_dir) }

            launch(Dispatchers.IO) {
                getTempDir().apply {
                    if (exists() && listFiles().isNullOrEmpty().not()) {
                        deleteRecursively()
                    }
                }
            }

            launch { runCatching { UpdateChecker.checkForUpdates("dev") } }

            // wait until UpdateManager is done, it should only take few milliseconds
            UpdateManager.inspect()

            // debug options
            startThemeFlipperIfNotRunning()
        }

        if (BuildConfig.DEBUG || Settings.anr_watchdog) {
            ANRWatchDog().start()
        }

        if (BuildConfig.DEBUG || Settings.strict_mode) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .apply {
                        detectAll()
                        penaltyLog()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
                                violation.printStackTrace()
                                violation.cause?.let { throw it }
                            }
                        }
                    }
                    .build()
            )
        }
    }
}
