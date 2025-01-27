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
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.MaterialColors
import com.google.gson.JsonParser
import com.rk.libcommons.application
import com.rk.libcommons.toastIt
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

private typealias onClick = OnClickListener

class SetupEditor(val editor: KarbonEditor, private val ctx: Context, val scope: CoroutineScope) {

    private var syntaxJob: Job? = null
    init {
        with(editor) {
            syntaxJob = scope.launch(Dispatchers.Main){
                ensureTextmateTheme(ctx)
            }
            val tabSize = PreferencesData.getString(PreferencesKeys.TAB_SIZE, "4").toInt()
            props.deleteMultiSpaces = tabSize
            tabWidth = tabSize
            props.deleteEmptyLineFast = false
            props.useICULibToSelectWords = true
            setPinLineNumber(PreferencesData.getBoolean(PreferencesKeys.PIN_LINE_NUMBER, false))
            isLineNumberEnabled =
                PreferencesData.getBoolean(PreferencesKeys.SHOW_LINE_NUMBERS, true)
            isCursorAnimationEnabled =
                PreferencesData.getBoolean(PreferencesKeys.CURSOR_ANIMATION_ENABLED, false)
            setTextSize(PreferencesData.getString(PreferencesKeys.TEXT_SIZE, "14").toFloat())
            getComponent(EditorAutoCompletion::class.java).isEnabled = true
            setWordwrap(
                PreferencesData.getBoolean(PreferencesKeys.WORD_WRAP_ENABLED, false),
                PreferencesData.getBoolean(PreferencesKeys.ANTI_WORD_BREAKING, true)
            )
            showSuggestions(PreferencesData.getBoolean(PreferencesKeys.SHOW_SUGGESTIONS, false))
            lineSpacingExtra = PreferencesData.getString(
                PreferencesKeys.LINE_SPACING, lineSpacingExtra.toString()
            ).toFloat()
            lineSpacingMultiplier = PreferencesData.getString(
                PreferencesKeys.LINE_SPACING_MULTIPLAYER, lineSpacingMultiplier.toString()
            ).toFloat()
            kotlin.runCatching { applyFont(this) }.onFailure {
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        ctx, "${it.message} \n falling back to the default font", Toast.LENGTH_LONG
                    ).show()
                    kotlin.runCatching {
                        editor.typefaceText =
                            Typeface.createFromAsset(editor.context.assets, "fonts/Default.ttf")
                    }
                }
            }

            getComponent(EditorAutoCompletion::class.java).setLayout(object :
                DefaultCompletionLayout() {
                override fun onApplyColorScheme(colorScheme: EditorColorScheme) {
                    val typedValue = TypedValue()
                    ctx.theme.resolveAttribute(
                        com.google.android.material.R.attr.colorSurface, typedValue, true
                    )
                    val colorSurface = typedValue.data
                    (completionList.parent as? ViewGroup)?.background = ColorDrawable(colorSurface)
                }
            })

        }
    }


    suspend fun setupLanguage(fileName: String) {
        when (fileName.substringAfterLast('.', "").trim()) {
            "java", "bsh" -> setLanguage("source.java")
            "html" -> setLanguage("text.html.basic")
            "htmx" -> setLanguage("text.html.htmx")
            "kt", "kts" -> setLanguage("source.kotlin")
            "py" -> setLanguage("source.python")
            "nim" -> setLanguage("source.nim")
            "xml" -> setLanguage("text.xml")
            "js" -> setLanguage("source.js")
            "ts" -> setLanguage("source.ts")
            "tsx" -> setLanguage("source.tsx")
            "jsx" -> setLanguage("source.js.jsx")
            "md" -> setLanguage("text.html.markdown")
            "c" -> setLanguage("source.c")
            "cpp", "h" -> setLanguage("source.cpp")
            "json" -> setLanguage("source.json")
            "css" -> setLanguage("source.css")
            "cs","csx" -> setLanguage("source.cs")
            "yml", "yaml", "cff" -> setLanguage("source.yaml")
            "sh", "bash" -> setLanguage("source.shell")
            "rs" -> setLanguage("source.rust")
            "lua" -> setLanguage("source.lua")
            "php" -> setLanguage("source.php")
            "smali" -> setLanguage("source.smali")
            "v", "coq" -> setLanguage("source.coq")
            "properties" -> setLanguage("source.java-properties")
        }
    }

    companion object {
        private var isInit = false
        private var activityInit = false
        private var darkThemeRegistry: ThemeRegistry? = null
        private var oledThemeRegistry: ThemeRegistry? = null
        private var lightThemeRegistry: ThemeRegistry? = null
        private val mutex = Mutex()
        private var job: Job? = null

        fun init(scope: CoroutineScope) {
            job = scope.launch {
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

        suspend fun waitForInit() = job?.let { if (it.isCompleted.not()) { it.join() } }

        suspend fun initActivity(
            activity: Activity, calculateColors: () -> kotlin.Pair<String, String>
        ) {
            if (!activityInit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val colors = calculateColors.invoke()
                    initTextMateTheme(activity, colors.first, colors.second)
                } else {
                    initTextMateTheme(activity, null, null)
                }

                activityInit = true
            }
        }


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
                Thread{
                    darcula.close()
                    darcula_oled.close()
                    quietlight.close()
                }.start()
            }
        }

        fun applyFont(editor: CodeEditor) {
            val fontPath = PreferencesData.getString(PreferencesKeys.SELECTED_FONT_PATH, "")
            if (fontPath.isNotEmpty()) {
                val isAsset =
                    PreferencesData.getBoolean(PreferencesKeys.IS_SELECTED_FONT_ASSEST, false)
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

    private suspend fun setLanguage(languageScopeName: String) = withContext(Dispatchers.IO){
        waitForInit()

        syntaxJob?.let { if (it.isCompleted.not()) { it.join() } }

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

    }


    private suspend fun ensureTextmateTheme(ctx: Context) {
        waitForInit()
        val darkTheme: Boolean = when (PreferencesData.getString(
            PreferencesKeys.DEFAULT_NIGHT_MODE, "-1"
        ).toInt()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> PreferencesData.isDarkMode(ctx)
        }

        val themeRegistry = when {
            darkTheme && PreferencesData.isOled() -> oledThemeRegistry
            darkTheme -> darkThemeRegistry
            else -> lightThemeRegistry
        }

        themeRegistry?.let {
            val editorColorScheme: EditorColorScheme = TextMateColorScheme.create(it)
            if (PreferencesData.isDarkMode(ctx) && PreferencesData.isOled()) {
                editorColorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.BLACK)
            }
            withContext(Dispatchers.Main) {
                editor.colorScheme = editorColorScheme
            }
        }
    }


    fun getInputView(): SymbolInputView {
        val darkTheme: Boolean = when (PreferencesData.getString(
            PreferencesKeys.DEFAULT_NIGHT_MODE, "-1"
        ).toInt()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> PreferencesData.isDarkMode(ctx)
        }

        return SymbolInputView(ctx).apply {
            textColor = if (darkTheme) {
                Color.WHITE
            } else {
                Color.BLACK
            }

            val keys = mutableListOf<Pair<String, View.OnClickListener>>().apply {
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
