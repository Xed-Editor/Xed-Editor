package com.rk

import android.app.Application
import android.os.Build
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.LocaleListCompat
import com.github.anrwatchdog.ANRWatchDog
import com.rk.activities.main.SessionManager
import com.rk.commands.CommandProvider
import com.rk.commands.KeybindingsManager
import com.rk.crashhandler.CrashHandler
import com.rk.editor.CodeHighlighter
import com.rk.editor.FontCache
import com.rk.editor.KeywordManager
import com.rk.editor.LanguageManager
import com.rk.icons.pack.IconPackManager
import com.rk.lsp.FileIconProvider
import com.rk.lsp.LspPersistence
import com.rk.lsp.MarkdownImageProvider
import com.rk.resources.Res
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.settings.debugOptions.startThemeFlipperIfNotRunning
import com.rk.settings.editor.DEFAULT_APP_FONT_PATH
import com.rk.settings.editor.DEFAULT_EDITOR_FONT_PATH
import com.rk.settings.editor.DEFAULT_TERMINAL_FONT_PATH
import com.rk.theme.updateThemes
import com.rk.utils.application
import com.rk.utils.getTempDir
import com.rk.xededitor.BuildConfig
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(DelicateCoroutinesApi::class)
open class App : Application() {
    companion object {
        val versionCode: Long by lazy {
            val app = application ?: throw IllegalStateException("Application is not initialized yet")
            PackageInfoCompat.getLongVersionCode(app.packageManager.getPackageInfo(app.packageName, 0))
        }

        private var _iconPackManager: IconPackManager? = null
        val iconPackManager: IconPackManager
            get() {
                if (_iconPackManager == null) {
                    _iconPackManager = IconPackManager(application!!)
                }

                return _iconPackManager!!
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
        FileIconProvider.register()

        CommandProvider.buildCommands()
        KeybindingsManager.loadKeybindings()

        val currentLocale = Locale.forLanguageTag(Settings.current_lang)
        val appLocale = LocaleListCompat.create(currentLocale)
        AppCompatDelegate.setApplicationLocales(appLocale)

        GlobalScope.launch(Dispatchers.IO) {
            launch(Dispatchers.IO) { iconPackManager.indexIconPacks() }

            launch { LanguageManager.initGrammarRegistry() }

            launch { KeywordManager.initKeywordRegistry(this@App) }

            launch { CodeHighlighter.registerMarkdownCodeHighlighter(this@App) }

            launch(Dispatchers.IO) { SessionManager.preloadSession() }

            launch(Dispatchers.IO) {
                val editorFontPath = Settings.editor_font_path.ifEmpty { DEFAULT_EDITOR_FONT_PATH }
                val isEditorAsset = if (editorFontPath.isNotEmpty()) Settings.is_editor_font_asset else true

                val appFontPath = Settings.app_font_path.ifEmpty { DEFAULT_APP_FONT_PATH }
                val isAppAsset = if (editorFontPath.isNotEmpty()) Settings.is_app_font_asset else true

                val terminalFontPath = Settings.terminal_font_path.ifEmpty { DEFAULT_TERMINAL_FONT_PATH }
                val isTerminalAsset = if (terminalFontPath.isNotEmpty()) Settings.is_terminal_font_asset else true

                FontCache.loadFont(this@App, editorFontPath, isEditorAsset)
                FontCache.loadFont(this@App, appFontPath, isAppAsset)
                FontCache.loadFont(this@App, terminalFontPath, isTerminalAsset)
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

            launch { runCatching { UpdateChecker.checkForUpdates("main") } }

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
