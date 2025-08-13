package com.rk.libcommons.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.MaterialColors
import com.google.gson.JsonParser
import com.rk.file.FileObject
import com.rk.libcommons.application
import com.rk.libcommons.errorDialog
import com.rk.libcommons.isDarkMode
import com.rk.libcommons.isMainThread
import com.rk.libcommons.toast
import com.rk.settings.Settings
import com.rk.xededitor.R
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.DefaultCompletionLayout
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset


@Suppress("NOTHING_TO_INLINE")
class KarbonEditor : CodeEditor {
    constructor(context: Context) : super(context)
    constructor(context: Context,postColor:()-> Unit) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)

    private val scope = CoroutineScope(Dispatchers.Default)
    private val themeRegistry: ThemeRegistry = ThemeRegistry()

    init {
        applyFont()
        applySettings()
    }


    fun updateColors(postColor:(EditorColorScheme)-> Unit){
        scope.launch(Dispatchers.IO) {
            val cacheKey = getCacheKey(context)
            val cachedScheme = colorSchemeCache[cacheKey]

            if (cachedScheme != null) {
                // Use cached scheme if available
                withContext(Dispatchers.Main) {
                    setColorScheme(cachedScheme)
                    postColor(colorScheme)
                }
            } else {
                // Load and cache the scheme if not available
                suspend fun load(path: String, name: String) = withContext(Dispatchers.IO) {
                    ThemeModel(IThemeSource.fromInputStream(context.assets.open(path), name, null))
                }

                val darkTheme = cacheKey.startsWith("dark")
                val amoled = cacheKey.endsWith("true")

                val themeModel = when {
                    darkTheme && amoled -> load(
                        "textmate/black/darcula.json",
                        "darcula.json"
                    )
                    darkTheme -> load("textmate/darcula.json", "darcula.json")
                    else -> load(
                        "textmate/quietlight.json",
                        "quietlight.json"
                    )
                }

                themeRegistry.loadTheme(themeModel)

                val colors = TextMateColorScheme.create(themeRegistry)

                // Cache the scheme
                colorSchemeCache[cacheKey] = colors

                withContext(Dispatchers.Main) {
                    setColorScheme(colors)
                    postColor(colorScheme)
                }
            }
        }
    }

    override fun release() {
        scope.cancel()
        super.release()
    }

    fun applySettings() {
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

        val tabSize = Settings.tab_size
        val pinLineNumber = Settings.pin_line_number
        val showLineNumber = Settings.show_line_numbers
        val cursorAnimation = Settings.cursor_animation
        val textSize = Settings.editor_text_size
        val wordWrap = Settings.wordwrap
        val keyboardSuggestion = Settings.show_suggestions
        val lineSpacing = Settings.line_spacing

        props.deleteMultiSpaces = tabSize
        tabWidth = tabSize
        props.deleteEmptyLineFast = false
        props.useICULibToSelectWords = true
        setPinLineNumber(pinLineNumber)
        isLineNumberEnabled = showLineNumber
        isCursorAnimationEnabled = cursorAnimation
        setTextSize(textSize.toFloat())
        isWordwrap = wordWrap
        lineSpacingExtra = lineSpacing
        isDisableSoftKbdIfHardKbdAvailable = Settings.hide_soft_keyboard_if_hardware
        showSuggestions(keyboardSuggestion)

    }


    fun applyFont() {
        runCatching {
            val fontPath = Settings.selected_font_path
            val font = if (fontPath.isNotEmpty()) {
                FontCache.getFont(context, fontPath, Settings.is_selected_font_assest) ?: FontCache.getFont(context, "fonts/Default.ttf", true)
            } else {
                FontCache.getFont(context, "fonts/Default.ttf", true)
            }

            typefaceText = if (font == null){
                Typeface.DEFAULT
            }else{
                font
            }
        }.onFailure {
            errorDialog(it)
        }
    }


    fun showSuggestions(yes: Boolean) {
        inputType = if (yes) {
            InputType.TYPE_TEXT_VARIATION_NORMAL
        } else {
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
    }



    companion object{
        private var isInit = false

        private val colorSchemeCache = mutableMapOf<String, TextMateColorScheme>()

        private fun getCacheKey(context: Context): String {
            val darkTheme = when (Settings.default_night_mode) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> isDarkMode(context)
            }
            return "${if (darkTheme) "dark" else "light"}_${Settings.amoled}"
        }

        suspend fun initGrammarRegistry() = withContext(Dispatchers.IO) {
            if (!isInit) {
                FileProviderRegistry.getInstance()
                    .addFileProvider(AssetsFileResolver(application!!.assets))
                GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
                isInit = true
            }
        }
    }


    suspend fun setLanguage(languageScopeName: String) = withContext(Dispatchers.IO) {
        while (!isInit && isActive) delay(50)

        val language = TextMateLanguage.create(languageScopeName, Settings.auto_complete).apply {
            if (Settings.auto_complete) {
                context.assets.open("textmate/keywords.json").use {
                    JsonParser.parseReader(InputStreamReader(it))
                        .asJsonObject[languageScopeName]?.asJsonArray
                        ?.map { el -> el.asString }
                        ?.toTypedArray()
                        ?.let(::setCompleterKeywords)
                }
            }
        }

        withContext(Dispatchers.Main) { setEditorLanguage(language as Language) }
    }
}
