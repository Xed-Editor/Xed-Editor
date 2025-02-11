package com.rk.libcommons.editor

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Pair
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import com.google.gson.JsonParser
import com.rk.libcommons.application
import com.rk.libcommons.safeLaunch
import com.rk.libcommons.toastCatching
import com.rk.libcommons.toastIt
import com.rk.settings.Settings
import com.rk.settings.SettingsKey
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolInputView
import io.github.rosemoe.sora.widget.component.DefaultCompletionLayout
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

private typealias onClick = OnClickListener

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
    "gitattributes" to "source.ini",
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
    "gitmodules" to "source.ini",
    "php" to "source.php",
    "ini" to "source.ini",
    "smali" to "source.smali",
    "v" to "source.coq",
    "gitconfig" to "source.ini",
    "coq" to "source.coq",
    "properties" to "source.java-properties"
)


suspend fun KarbonEditor.applySettings() {
    withContext(Dispatchers.IO) {
        val tabSize = Settings.getString(SettingsKey.TAB_SIZE, "4").toInt()
        val pinLineNumber = Settings.getBoolean(SettingsKey.PIN_LINE_NUMBER, false)
        val showLineNumber = Settings.getBoolean(SettingsKey.SHOW_LINE_NUMBERS, true)
        val cursorAnimation = Settings.getBoolean(SettingsKey.CURSOR_ANIMATION_ENABLED, false)
        val textSize = Settings.getString(SettingsKey.TEXT_SIZE, "14").toFloat()
        val wordWrap = Settings.getBoolean(SettingsKey.WORD_WRAP_ENABLED, false)
        val keyboardSuggestion = Settings.getBoolean(SettingsKey.SHOW_SUGGESTIONS, false)
        val always_show_soft_keyboard =
            Settings.getBoolean(SettingsKey.ALWAYS_SHOW_SOFT_KEYBOARD, false)
        val lineSpacing = Settings.getString(
            SettingsKey.LINE_SPACING, lineSpacingExtra.toString()
        ).toFloat()
        val lineMultiplier = Settings.getString(
            SettingsKey.LINE_SPACING_MULTIPLAYER, lineSpacingMultiplier.toString()
        ).toFloat()

        withContext(Dispatchers.Main) {
            props.deleteMultiSpaces = tabSize
            tabWidth = tabSize
            props.deleteEmptyLineFast = false
            props.useICULibToSelectWords = true
            setPinLineNumber(pinLineNumber)
            isLineNumberEnabled = showLineNumber
            isCursorAnimationEnabled = cursorAnimation
            setTextSize(textSize)
            isWordwrap = wordWrap
            showSuggestions(keyboardSuggestion)
            lineSpacingExtra = lineSpacing
            lineSpacingMultiplier = lineMultiplier
            isDisableSoftKbdIfHardKbdAvailable = always_show_soft_keyboard.not()
        }
    }


}

class SetupEditor(val editor: KarbonEditor, private val ctx: Context, val scope: CoroutineScope) {

    private var syntaxJob: Job? = null

    init {
        with(editor) {
            syntaxJob = scope.safeLaunch(Dispatchers.Main) {
                ensureTextmateTheme(ctx)
            }
            getComponent(EditorAutoCompletion::class.java).isEnabled = true
            scope.safeLaunch { applySettings() }

            toastCatching { applyFont(this) }?.let {
                toastCatching {
                    typefaceText = Typeface.createFromAsset(context.assets, "fonts/Default.ttf")
                }
            }

            getComponent(EditorAutoCompletion::class.java).setLayout(object :
                DefaultCompletionLayout() {
                override fun onApplyColorScheme(colorScheme: EditorColorScheme) {
                    val typedValue = TypedValue()
                    context.theme.resolveAttribute(
                        com.google.android.material.R.attr.colorSurface, typedValue, true
                    )
                    val colorSurface = typedValue.data
                    (completionList.parent as? ViewGroup)?.background = ColorDrawable(colorSurface)
                }
            })
        }
    }


    suspend fun setupLanguage(fileName: String) {
        val source = when (fileName) {
            "gradlew" -> textmateSources["sh"]
            else -> {
                textmateSources[fileName.substringAfterLast('.', "").trim()]
            }
        }
        source?.let { setLanguage(it) }
    }

    companion object {
        private var isInit = false
        private var activityInit = false
        private var darkThemeRegistry: ThemeRegistry? = null
        private var oledThemeRegistry: ThemeRegistry? = null
        private var lightThemeRegistry: ThemeRegistry? = null
        private val mutex = Mutex()
        private var job: Job? = null
        private var activityjob: Job? = null

        fun init(scope: CoroutineScope) {
            job = scope.safeLaunch {
                mutex.withLock {
                    if (!isInit) {
                        withContext(Dispatchers.IO) {
                            FileProviderRegistry.getInstance()
                                .addFileProvider(AssetsFileResolver(application!!.assets))
                            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
                        }
                        isInit = true
                    }
                }
            }
        }

        suspend fun waitForInit() = job?.let {
            if (it.isCompleted.not()) {
                it.join()
            }
        }

        suspend fun waitForActivityInit() = activityjob?.let {
            if (it.isCompleted.not()) {
                it.join()
            }
        }

        suspend fun initActivity(
            activity: Activity, calculateColors: () -> kotlin.Pair<String, String>
        ) {
            if (!activityInit) {
                activityjob = GlobalScope.launch {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val colors = calculateColors.invoke()
                        initTextMateTheme(activity, colors.first, colors.second)
                    } else {
                        initTextMateTheme(activity, null, null)
                    }
                }
                activityjob?.join()
                activityInit = true
            }


        }


        @OptIn(DelicateCoroutinesApi::class)
        private suspend fun initTextMateTheme(
            ctx: Context, darkSurfaceColor: String?, lightSurfaceColor: String?
        ) {
            waitForInit()
            darkThemeRegistry = ThemeRegistry()
            oledThemeRegistry = ThemeRegistry()
            lightThemeRegistry = ThemeRegistry()

            val darcula = ctx.assets.open("textmate/darcula.json")
            val darcula_oled = ctx.assets.open("textmate/black/darcula.json")
            val quietlight = ctx.assets.open("textmate/quietlight.json")

            try {
                oledThemeRegistry?.loadTheme(
                    ThemeModel(IThemeSource.fromInputStream(darcula_oled, "darcula.json", null))
                )


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    fun read(inputStream: InputStream, replacements: Map<String, String>): String {
                        val content = inputStream.bufferedReader().use { it.readText() }
                        var modifiedContent = content
                        for ((oldValue, newValue) in replacements) {
                            modifiedContent = modifiedContent.replace(oldValue, newValue)
                        }

                        return modifiedContent
                    }


                    lightThemeRegistry?.loadTheme(
                        ThemeModel(
                            IThemeSource.fromString(
                                IThemeSource.ContentType.JSON,
                                read(quietlight, mapOf("#FAF9FF" to lightSurfaceColor!!))
                            )
                        )
                    )

                    darkThemeRegistry?.loadTheme(
                        ThemeModel(
                            IThemeSource.fromString(
                                IThemeSource.ContentType.JSON,
                                read(darcula, mapOf("#1C1B20" to darkSurfaceColor!!))
                            )
                        )
                    )
                } else {
                    darkThemeRegistry?.loadTheme(
                        ThemeModel(IThemeSource.fromInputStream(darcula, "darcula.json", null))
                    )
                    lightThemeRegistry?.loadTheme(
                        ThemeModel(
                            IThemeSource.fromInputStream(
                                quietlight, "quietlight.json", null
                            )
                        )
                    )
                }


            } catch (e: Exception) {
                e.printStackTrace()
                e.message.toastIt()
            } finally {
                GlobalScope.safeLaunch(Dispatchers.IO) {
                    darcula.close()
                    darcula_oled.close()
                    quietlight.close()
                }
            }
        }

        fun applyFont(editor: CodeEditor) {
            val fontPath = Settings.getString(SettingsKey.SELECTED_FONT_PATH, "")
            if (fontPath.isNotEmpty()) {
                val isAsset =
                    Settings.getBoolean(SettingsKey.IS_SELECTED_FONT_ASSEST, false)
                if (isAsset) {
                    editor.typefaceText = Typeface.createFromAsset(editor.context.assets, fontPath)
                } else {
                    editor.typefaceText = Typeface.createFromFile(File(fontPath))
                }
            } else {
                println("fallback: font Path is empty")
                editor.typefaceText =
                    Typeface.createFromAsset(editor.context.assets, "fonts/Default.ttf")
            }
            editor.invalidate()
        }
    }

    private val languageMutex = Mutex()
    suspend fun setLanguage(languageScopeName: String) = withContext(Dispatchers.IO) {
        waitForInit()

        syntaxJob!!.let {
            if (it.isCompleted.not()) {
                it.join()
            }
        }
        languageMutex.lock()

        val language = TextMateLanguage.create(languageScopeName, true)
        val kw = ctx.assets.open("textmate/keywords.json")
        val reader = InputStreamReader(kw)
        val jsonElement = JsonParser.parseReader(reader)
        val keywordsArray = jsonElement.asJsonObject.getAsJsonArray(languageScopeName)

        if (keywordsArray != null) {
            val keywords = Array(keywordsArray.size()) { "" }
            for (i in keywords.indices) {
                keywords[i] = keywordsArray[i].asString
            }
            language.setCompleterKeywords(keywords)
        }

        withContext(Dispatchers.Main) {
            editor.setEditorLanguage(language as Language)
        }
        languageMutex.unlock()
    }


    private suspend fun ensureTextmateTheme(ctx: Context) {
        waitForInit()
        waitForActivityInit()
        val darkTheme: Boolean = when (Settings.getString(
            SettingsKey.DEFAULT_NIGHT_MODE, "-1"
        ).toInt()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> Settings.isDarkMode(ctx)
        }

        val themeRegistry = when {
            darkTheme && Settings.isOled() -> oledThemeRegistry
            darkTheme -> darkThemeRegistry
            else -> lightThemeRegistry
        }

        themeRegistry?.let {
            val editorColorScheme: EditorColorScheme = TextMateColorScheme.create(it)
            if (Settings.isDarkMode(ctx) && Settings.isOled()) {
                editorColorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.BLACK)
            }
            withContext(Dispatchers.Main) {
                editor.colorScheme = editorColorScheme
            }
        }
    }


    fun getInputView(): SymbolInputView {
        val darkTheme: Boolean = when (Settings.getString(
            SettingsKey.DEFAULT_NIGHT_MODE, "-1"
        ).toInt()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> Settings.isDarkMode(ctx)
        }

        return SymbolInputView(ctx).apply {
            textColor = if (darkTheme) {
                Color.WHITE
            } else {
                Color.BLACK
            }

            val keys = mutableListOf<Pair<String, OnClickListener>>().apply {
                add(Pair("->", onClick {
                    editor.onKeyDown(
                        KeyEvent.KEYCODE_TAB, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB)
                    )
                }))

                add(Pair("⌘", onClick {
                    EventBus.getDefault().post(ControlPanel())
                }))

                add(Pair("←", onClick {
                    editor.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
                    )
                }))

                add(Pair("↑", onClick {
                    editor.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
                    )

                }))

                add(Pair("→", onClick {
                    editor.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
                    )

                }))

                add(Pair("↓", onClick {
                    editor.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
                    )

                }))

                add(Pair("⇇", onClick {
                    editor.onKeyDown(
                        KeyEvent.KEYCODE_MOVE_HOME,
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME)
                    )
                }))

                add(Pair("⇉", onClick {
                    editor.onKeyDown(
                        KeyEvent.KEYCODE_MOVE_END,
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END)
                    )
                }))
            }

            addSymbols(keys.toTypedArray())

            addSymbols(
                arrayOf("(", ")", "\"", "{", "}", "[", "]", ";"),
                arrayOf("(", ")", "\"", "{", "}", "[", "]", ";")
            )

            bindEditor(editor)
        }
    }
}
