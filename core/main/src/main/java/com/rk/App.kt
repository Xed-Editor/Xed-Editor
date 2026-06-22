package com.rk

import android.app.Application
import android.os.Build
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
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
import com.rk.extension.ExtensionAPIManager
import com.rk.extension.ExtensionManager
import com.rk.extension.loadAllExtensions
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
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.*

@OptIn(DelicateCoroutinesApi::class)
class App : Application() {

    init {
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        application = this
        Res.application = this
        XedManager.init(this)

        updateThemes()

        MarkdownImageProvider.register()
        FileIconProvider.register()

        val currentLocale = Locale.forLanguageTag(Settings.current_lang)
        val appLocale = LocaleListCompat.create(currentLocale)
        AppCompatDelegate.setApplicationLocales(appLocale)

        val extensionManager = XedManager.extensionManager
        val iconPackManager = XedManager.iconPackManager

        AppScope.safeLaunch(context = AppDispatchers.IO) {
            launch(AppDispatchers.IO) {
                LspPersistence.restoreServers()
            }

            launch(AppDispatchers.IO) {
                CommandProvider.buildCommands()
                KeybindingsManager.loadKeybindings()
            }

            launch(AppDispatchers.IO) {
                extensionManager.indexLocalExtensions()
                extensionManager.loadAllExtensions()
                registerActivityLifecycleCallbacks(ExtensionAPIManager)
            }

            launch(AppDispatchers.IO) { iconPackManager.indexIconPacks() }

            launch(AppDispatchers.Startup) { LanguageManager.initGrammarRegistry() }

            launch(AppDispatchers.Startup) { KeywordManager.initKeywordRegistry(this@App) }

            launch(AppDispatchers.Startup) { CodeHighlighter.registerMarkdownCodeHighlighter(this@App) }

            launch(AppDispatchers.IO) { SessionManager.preloadSession() }

            launch(AppDispatchers.IO) {
                val editorFontPath = Settings.editor_font_path.ifEmpty { DEFAULT_EDITOR_FONT_PATH }
                val isEditorAsset = if (Settings.editor_font_path.isNotEmpty()) Settings.is_editor_font_asset else true

                val appFontPath = Settings.app_font_path.ifEmpty { DEFAULT_APP_FONT_PATH }
                val isAppAsset = if (Settings.app_font_path.isNotEmpty()) Settings.is_app_font_asset else true

                val terminalFontPath = Settings.terminal_font_path.ifEmpty { DEFAULT_TERMINAL_FONT_PATH }
                val isTerminalAsset = if (Settings.terminal_font_path.isNotEmpty()) Settings.is_terminal_font_asset else true

                FontCache.loadFont(this@App, editorFontPath, isEditorAsset)
                FontCache.loadFont(this@App, appFontPath, isAppAsset)
                FontCache.loadFont(this@App, terminalFontPath, isTerminalAsset)
            }

            launch(AppDispatchers.Startup) { Preference.preloadAllSettings() }

            launch { DocumentProvider.setDocumentProviderEnabled(this@App, Settings.expose_home_dir) }

            launch(AppDispatchers.IO) {
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
