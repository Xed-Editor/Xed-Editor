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
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.CURRENT_LINE
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LINE_DIVIDER
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LINE_NUMBER
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LINE_NUMBER_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LINE_NUMBER_CURRENT
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LINE_NUMBER_PANEL_TEXT
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.MATCHED_TEXT_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SCROLL_BAR_THUMB
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SCROLL_BAR_THUMB_PRESSED
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SELECTED_TEXT_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.WHOLE_BACKGROUND
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



    fun setThemeColors(editorSurface: Int,surfaceContainer:Int,
                       surface: Int,onSurface: Int,
                       colorPrimary: Int,
                       colorPrimaryContainer: Int,
                       colorSecondary: Int,
                       secondaryContainer: Int,
                       selectionBg: Int,
                       handleColor: Int,
                       gutterColor: Int,
                       currentLine: Int,
                       dividerColor:Int) {
        updateColors { colors ->
            with(colors){
                setColor(HIGHLIGHTED_DELIMITERS_UNDERLINE, Color.TRANSPARENT)

                fun EditorColorScheme.setColors(color: Int, vararg keys: Int) {
                    keys.forEach { setColor(it, color) }
                }

                setColors(
                    onSurface,
                    TEXT_ACTION_WINDOW_ICON_COLOR,
                    COMPLETION_WND_TEXT_PRIMARY,
                    COMPLETION_WND_TEXT_SECONDARY
                )

                setColors(
                    editorSurface,
                    WHOLE_BACKGROUND,
                )

                setColors(onSurface,LINE_NUMBER,LINE_NUMBER_CURRENT)


                setColors(surfaceContainer,
                    TEXT_ACTION_WINDOW_BACKGROUND,
                    COMPLETION_WND_BACKGROUND)


                setColors(handleColor,EditorColorScheme.SELECTION_HANDLE)
                setColors(selectionBg,EditorColorScheme.SELECTION_INSERT,MATCHED_TEXT_BACKGROUND,SELECTED_TEXT_BACKGROUND)
                setColors(colorPrimary,HIGHLIGHTED_DELIMITERS_FOREGROUND)

                setColors(
                    colorPrimary,
                    EditorColorScheme.BLOCK_LINE,
                    EditorColorScheme.BLOCK_LINE_CURRENT,
                )


                setColors(setAlpha(onSurface,0.3f),SCROLL_BAR_THUMB)
                setColors(setAlpha(onSurface,0.2f),SCROLL_BAR_THUMB_PRESSED)

                setColors(currentLine,CURRENT_LINE)
                setColors(gutterColor,LINE_NUMBER_BACKGROUND)
                setColors(dividerColor,LINE_DIVIDER)


            }
        }
    }


    private fun setAlpha(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        val newAlpha = (a * factor).toInt().coerceIn(0, 255)
        return Color.argb(newAlpha, r, g, b)
    }


    private fun updateColors(postAndPreColor:(EditorColorScheme)-> Unit){
        postAndPreColor.invoke(colorScheme)
        scope.launch(Dispatchers.IO) {
            val cacheKey = getCacheKey(context)
            val cachedScheme = colorSchemeCache[cacheKey]

            if (cachedScheme != null) {
                // Use cached scheme if available
                withContext(Dispatchers.Main) {
                    setColorScheme(cachedScheme)
                    postAndPreColor(colorScheme)
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
                    postAndPreColor(colorScheme)
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

        val language = TextMateLanguage.create(languageScopeName, Settings.textMateSuggestion).apply {
            if (Settings.textMateSuggestion){
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
