package com.rk.libcommons.editor

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.gson.JsonParser
import com.rk.libcommons.application
import com.rk.libcommons.isDarkMode
import com.rk.libcommons.toastIt
import com.rk.settings.Settings
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_ITEM_CURRENT
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.MATCHED_TEXT_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SELECTED_TEXT_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.InputStream
import java.io.InputStreamReader

val textmateSources = hashMapOf(
    "pro" to "source.shell",
    "java" to "source.java",
    "bsh" to "source.java",
    "html" to "text.html.basic",
    "htmx" to "text.html.htmx",
    "gsh" to "source.groovy",
    "kt" to "source.kotlin",
    "kts" to "source.kotlin",
    "py" to "source.python",
    "groovy" to "source.groovy",
    "nim" to "source.nim",
    "xml" to "text.xml",
    "go" to "source.go",
    "js" to "source.js",
    "ts" to "source.ts",
    "gradle" to "source.groovy",
    "tsx" to "source.tsx",
    "jsx" to "source.js.jsx",
    "md" to "text.html.markdown",
    "c" to "source.c",
    "bat" to "source.batchfile",
    "cpp" to "source.cpp",
    "h" to "source.cpp",
    "xhtml" to "text.html.basic",
    "json" to "source.json",
    "css" to "source.css",
    "gvy" to "source.groovy",
    "cs" to "source.cs",
    "csx" to "source.cs",
    "xht" to "text.html.basic",
    "yml" to "source.yaml",
    "yaml" to "source.yaml",
    "gy" to "source.groovy",
    "cff" to "source.yaml",
    "cmd" to "source.batchfile",
    "sh" to "source.shell",
    "bash" to "source.shell",
    "htm" to "text.html.basic",
    "rs" to "source.rust",
    "lua" to "source.lua",
    "php" to "source.php",
    "ini" to "source.ini",
    "smali" to "source.smali",
    "v" to "source.coq",
    "coq" to "source.coq",
    "properties" to "source.java-properties",
    "mut" to "source.js",
    "latex" to "text.tex.latex",
    "tex" to "text.tex.latex",
    "ltx" to "text.tex.latex",
    "toml" to "source.toml",
    "dart" to "source.dart",
)

class SetupEditor(
    val editor: KarbonEditor,
    private val ctx: Context,
    val scope: CoroutineScope
) {
    init {
        scope.launch(Dispatchers.IO) {
            ensureTextmateTheme(ctx)
        }
    }

    private fun getSourceForSpacialFileName(fileName: String): String? {
        return when (fileName) {
            "gradlew" -> textmateSources["sh"]
            "logcat.txt" -> {
                editor.isWordwrap = Settings.wordwrap
                textmateSources["log"]
            }

            else -> null
        }
    }

    suspend fun setupLanguage(fileName: String) {
        mutex.withLock {
            val source = getSourceForSpacialFileName(fileName)
                ?: textmateSources[fileName.substringAfterLast('.', "").trim()]
            source?.let { setLanguage(it) }
        }
    }

    companion object {
        private val mutex = Mutex()
        private var isInit = false
        private var activityInit = false

        private var darkThemeRegistry: ThemeRegistry? = null
        private var oledThemeRegistry: ThemeRegistry? = null
        private var lightThemeRegistry: ThemeRegistry? = null

        @OptIn(DelicateCoroutinesApi::class)
        fun init() {
            GlobalScope.launch(Dispatchers.IO) {
                if (!isInit) {
                    FileProviderRegistry.getInstance()
                        .addFileProvider(AssetsFileResolver(application!!.assets))
                    GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
                    isInit = true
                }
            }
        }

        @OptIn(DelicateCoroutinesApi::class)
        fun initActivity(activity: Activity, calculateColors: () -> Pair<String, String>) {
            if (!activityInit) {
                GlobalScope.launch(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val colors = calculateColors.invoke()
                        initTextMateTheme(activity, colors.first, colors.second)
                    } else {
                        initTextMateTheme(activity, null, null)
                    }
                }
                activityInit = true
            }
        }

        private fun initTextMateTheme(
            ctx: Context,
            darkSurfaceColor: String?,
            lightSurfaceColor: String?
        ) {
            darkThemeRegistry = ThemeRegistry()
            oledThemeRegistry = ThemeRegistry()
            lightThemeRegistry = ThemeRegistry()

            try {
                darkThemeRegistry?.loadTheme(
                    ThemeModel(
                        IThemeSource.fromInputStream(
                            ctx.assets.open(
                                "textmate/darcula.json"
                            ), "darcula.json", null
                        )
                    )
                )
                oledThemeRegistry?.loadTheme(
                    ThemeModel(
                        IThemeSource.fromInputStream(
                            ctx.assets.open(
                                "textmate/black/darcula.json"
                            ), "darcula.json", null
                        )
                    )
                )
                lightThemeRegistry?.loadTheme(
                    ThemeModel(
                        IThemeSource.fromInputStream(
                            ctx.assets.open(
                                "textmate/quietlight.json"
                            ), "quietlight.json", null
                        )
                    )
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    assert(darkSurfaceColor != null)
                    assert(lightSurfaceColor != null)

                    fun read(inputStream: InputStream, replacements: Map<String, String>) =
                        inputStream.bufferedReader().use { it.readText() }.let { content ->
                            replacements.entries.fold(content) { acc, (old, new) ->
                                acc.replace(
                                    old,
                                    new
                                )
                            }
                        }

                    lightThemeRegistry?.loadTheme(
                        ThemeModel(
                            IThemeSource.fromString(
                                IThemeSource.ContentType.JSON,
                                read(
                                    ctx.assets.open("textmate/quietlight.json"),
                                    mapOf("#FAF9FF" to lightSurfaceColor!!)
                                )
                            )
                        )
                    )

                    darkThemeRegistry?.loadTheme(
                        ThemeModel(
                            IThemeSource.fromString(
                                IThemeSource.ContentType.JSON,
                                read(
                                    ctx.assets.open("textmate/darcula.json"),
                                    mapOf("#1C1B20" to darkSurfaceColor!!)
                                )
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                e.message.toastIt()
            }
        }
    }

    suspend fun setLanguage(languageScopeName: String) = withContext(Dispatchers.IO) {
        val language = if (languageScopeName != "text.plain") {
            TextMateLanguage.create(languageScopeName, Settings.auto_complete).apply {
                if (Settings.auto_complete) {
                    ctx.assets.open("textmate/keywords.json").use { inputStream ->
                        JsonParser.parseReader(InputStreamReader(inputStream)).asJsonObject
                            .getAsJsonArray(languageScopeName)?.map { it.asString }?.toTypedArray()
                            ?.let { setCompleterKeywords(it) }
                    }
                }
            }
        } else {
            PlainTextLanguage()
        }

        withContext(Dispatchers.Main) { editor.setEditorLanguage(language as Language) }
    }

    private fun ensureTextmateTheme(ctx: Context) {
        val darkTheme = when (Settings.default_night_mode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> isDarkMode(ctx)
        }

        val themeRegistry = when {
            darkTheme && Settings.amoled -> oledThemeRegistry
            darkTheme -> darkThemeRegistry
            else -> lightThemeRegistry
        }

        themeRegistry?.let {
            val editorColorScheme = TextMateColorScheme.create(it).apply {
                if (darkTheme && Settings.amoled) setColor(
                    EditorColorScheme.WHOLE_BACKGROUND,
                    Color.BLACK
                )
            }

            scope.launch(Dispatchers.Main) {
                editor.colorScheme = editorColorScheme
                editor.colorScheme.let { colorScheme ->
                    val typedValue = TypedValue()
                    val theme = ctx.theme
                    theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)

                    val colorPrimary = ContextCompat.getColor(ctx, typedValue.resourceId)

                    
                    val transparentColor = Color.argb(
                        130,
                        Color.red(colorPrimary),
                        Color.green(colorPrimary),
                        Color.blue(colorPrimary)
                    )

                    colorScheme.setColor(EditorColorScheme.SELECTION_HANDLE, colorPrimary)
                    colorScheme.setColor(EditorColorScheme.SELECTION_INSERT, colorPrimary)
                    colorScheme.setColor(EditorColorScheme.BLOCK_LINE, colorPrimary)
                    colorScheme.setColor(EditorColorScheme.BLOCK_LINE_CURRENT, colorPrimary)

                    colorScheme.setColor(
                        SELECTED_TEXT_BACKGROUND,
                        transparentColor
                    )
                    //colorScheme.setColor(EditorColorScheme.FUNCTION_CHAR_BACKGROUND_STROKE,transparentColor)

                    //search match text color
                    colorScheme.setColor(MATCHED_TEXT_BACKGROUND, colorPrimary)

                    colorScheme.setColor(SELECTED_TEXT_BACKGROUND, transparentColor)
                    //setColor(FUNCTION_CHAR_BACKGROUND_STROKE,transparentColor)

                    //bracket
                    colorScheme.setColor(HIGHLIGHTED_DELIMITERS_UNDERLINE, Color.TRANSPARENT)
                    colorScheme.setColor(HIGHLIGHTED_DELIMITERS_FOREGROUND, colorPrimary)

                    val darkTheme: Boolean = when (Settings.default_night_mode) {
                        AppCompatDelegate.MODE_NIGHT_YES -> true
                        AppCompatDelegate.MODE_NIGHT_NO -> false
                        else -> isDarkMode(ctx)
                    }

                    val surface = if (darkTheme) {
                        Color.BLACK
                    } else {
                        Color.WHITE
                    }

                    val onSurface = if (darkTheme) {
                        Color.WHITE
                    } else {
                        Color.BLACK
                    }

                    colorScheme.setColor(TEXT_ACTION_WINDOW_BACKGROUND, surface)
                    colorScheme.setColor(COMPLETION_WND_BACKGROUND, surface)

                    colorScheme.setColor(TEXT_ACTION_WINDOW_ICON_COLOR, onSurface)
                    colorScheme.setColor(COMPLETION_WND_TEXT_PRIMARY, onSurface)
                    colorScheme.setColor(COMPLETION_WND_TEXT_SECONDARY, onSurface)

                    colorScheme.setColor(COMPLETION_WND_ITEM_CURRENT, transparentColor)

                }

            }
        }
    }
}
