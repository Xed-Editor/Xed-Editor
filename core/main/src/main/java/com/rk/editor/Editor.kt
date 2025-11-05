package com.rk.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.unit.Density
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.rk.settings.Settings
import com.rk.theme.currentTheme
import com.rk.utils.application
import com.rk.utils.errorDialog
import com.rk.utils.isDarkMode
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.TextActionItem
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

@Suppress("NOTHING_TO_INLINE")
class Editor : CodeEditor {
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
        lineNumberMarginLeft = 9f

        getComponent<EditorAutoCompletion>(EditorAutoCompletion::class.java).apply {
            val metrics = context.resources.displayMetrics
            val density = Density(
                density = metrics.density,
                fontScale = context.resources.configuration.fontScale
            )

            setAdapter(AutoCompletionLayoutAdapter(density))
        }
    }

    fun setThemeColors(
        isDarkMode: Boolean,
        editorSurface: Int,
        surfaceContainer: Int,
        surface: Int,
        onSurface: Int,
        highSurfaceContainer: Int,
        colorPrimary: Int,
        colorPrimaryContainer: Int,
        colorSecondary: Int,
        secondaryContainer: Int,
        selectionBg: Int,
        handleColor: Int,
        gutterColor: Int,
        currentLine: Int,
        dividerColor: Int
    ) {
        updateColors { colors ->
            with(colors) {
                setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, Color.TRANSPARENT)

                fun EditorColorScheme.setColors(color: Int, vararg types: Int) {
                    types.forEach { setColor(it, color) }
                }

                setColors(
                    editorSurface,
                    EditorColorScheme.WHOLE_BACKGROUND,
                )

                setColors(
                    surfaceContainer,
                    EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND,
                    EditorColorScheme.COMPLETION_WND_BACKGROUND,
                    EditorColorScheme.DIAGNOSTIC_TOOLTIP_BACKGROUND,
                    EditorColorScheme.SIGNATURE_BACKGROUND,
                    EditorColorScheme.HOVER_BACKGROUND
                )

                setColors(
                    highSurfaceContainer,
                    EditorColorScheme.COMPLETION_WND_ITEM_CURRENT
                )

                setColors(
                    onSurface,
                    EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR,
                    EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY,
                    EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY,
                    EditorColorScheme.DIAGNOSTIC_TOOLTIP_BRIEF_MSG,
                    EditorColorScheme.DIAGNOSTIC_TOOLTIP_DETAILED_MSG,
                    EditorColorScheme.SIGNATURE_TEXT_NORMAL,
                    EditorColorScheme.HOVER_TEXT_NORMAL,
                    EditorColorScheme.LINE_NUMBER,
                    EditorColorScheme.LINE_NUMBER_CURRENT
                )

                setColors(
                    handleColor,
                    EditorColorScheme.SELECTION_HANDLE
                )
                setColors(
                    selectionBg,
                    EditorColorScheme.SELECTION_INSERT,
                    EditorColorScheme.MATCHED_TEXT_BACKGROUND,
                    EditorColorScheme.SELECTED_TEXT_BACKGROUND
                )
                setColors(
                    colorPrimary,
                    EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND,
                    EditorColorScheme.SIGNATURE_TEXT_HIGHLIGHTED_PARAMETER,
                    EditorColorScheme.HOVER_TEXT_HIGHLIGHTED,
                    EditorColorScheme.DIAGNOSTIC_TOOLTIP_ACTION,
                    EditorColorScheme.COMPLETION_WND_TEXT_MATCHED
                )

                setColors(setAlpha(onSurface, 0.6f), EditorColorScheme.BLOCK_LINE_CURRENT)
                setColors(
                    setAlpha(onSurface, 0.4f),
                    EditorColorScheme.NON_PRINTABLE_CHAR,
                    EditorColorScheme.BLOCK_LINE
                )
                setColors(setAlpha(onSurface, 0.3f), EditorColorScheme.SCROLL_BAR_THUMB)
                setColors(setAlpha(onSurface, 0.2f), EditorColorScheme.SCROLL_BAR_THUMB_PRESSED)

                setColors(currentLine, EditorColorScheme.CURRENT_LINE)
                setColors(gutterColor, EditorColorScheme.LINE_NUMBER_BACKGROUND)
                setColors(
                    dividerColor,
                    EditorColorScheme.LINE_DIVIDER,
                    EditorColorScheme.STICKY_SCROLL_DIVIDER
                )

                val editorColors = if (isDarkMode) {
                    currentTheme.value?.darkEditorColors
                } else {
                    currentTheme.value?.lightEditorColors
                }

                if (editorColors.isNullOrEmpty().not()) {
                    editorColors.forEach {
                        setColor(it.key, it.color)
                    }
                }
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

    private fun updateColors(postAndPreColor: (EditorColorScheme) -> Unit) {
        postAndPreColor(colorScheme)
        scope.launch(Dispatchers.IO) {
            val cacheKey = getCacheKey(context)
            val cachedScheme = colorSchemeCache[cacheKey]

            if (cachedScheme != null) {
                // Use cached scheme if available
                withContext(Dispatchers.Main) {
                    colorScheme = cachedScheme
                    postAndPreColor(colorScheme)
                }
            } else {
                // Load and cache the scheme if not available
                val darkTheme = cacheKey.startsWith("dark")
                val amoled = cacheKey.endsWith("true")

                val themeModel = when {
                    darkTheme && amoled -> buildThemeModel("textmate/black/darcula.json", "darcula.json", true)
                    darkTheme -> buildThemeModel("textmate/darcula.json", "darcula.json", true)
                    else -> buildThemeModel("textmate/quietlight.json", "quietlight.json", false)
                }

                themeRegistry.loadTheme(themeModel)

                val colors = TextMateColorScheme.create(themeRegistry)

                // Cache the scheme
                colorSchemeCache[cacheKey] = colors

                withContext(Dispatchers.Main) {
                    colorScheme = colors
                    postAndPreColor(colorScheme)
                }
            }
        }
    }

    /**
     * Build a [ThemeModel] by applying the user's theme onto a base theme that
     * serves as a fallback.
     *
     * The method:
     * 1. Reads the JSON base theme file from the specified asset path.
     * 2. Merges additional token colors from the currently selected app theme.
     * 3. Converts the modified JSON back into a byte stream and builds a [ThemeModel].
     *
     * @param basePath The relative path of the base theme file inside the app's assets directory.
     * @param baseName The logical name of the theme (used when creating the [ThemeModel]).
     * @param darkTheme Whether to apply the dark variant of the current app theme’s token colors.
     * @return A [ThemeModel] representing the base theme (as a fallback) merged with the user's theme.
     */
    private suspend fun buildThemeModel(basePath: String, baseName: String, darkTheme: Boolean) =
        withContext(Dispatchers.IO) {
            val inputStream = context.assets.open(basePath)
            InputStreamReader(inputStream).use { reader ->
                val jsonElement = JsonParser.parseReader(reader)
                val jsonObject = jsonElement.asJsonObject

                val selectedTheme = currentTheme.value
                val tokenArray = when {
                    selectedTheme == null -> JsonArray()
                    darkTheme -> selectedTheme.darkTokenColors
                    else -> selectedTheme.lightTokenColors
                }

                // In some TextMate theme files the token colors are saved in an array
                // called settings and in some it's called tokenColors
                val arrayName = if (jsonObject.has("settings")) {
                    "settings"
                } else if (jsonObject.has("tokenColors")) {
                    "tokenColors"
                } else null

                if (!tokenArray.isEmpty) {
                    if (arrayName != null) {
                        val existingTokenColors = jsonObject[arrayName].asJsonArray
                        existingTokenColors.addAll(tokenArray)
                    } else {
                        jsonObject.add("tokenColors", tokenArray)
                    }
                }

                val bytes = jsonObject.toString().toByteArray(Charsets.UTF_8)
                val bais = ByteArrayInputStream(bytes)
                ThemeModel(IThemeSource.fromInputStream(bais, baseName, null))
            }
        }

    override fun release() {
        scope.cancel()
        super.release()
    }

    fun applySettings() {
        val tabSize = Settings.tab_size
        val pinLineNumber = Settings.pin_line_number
        val stickyScroll = Settings.sticky_scroll
        val fastDelete = Settings.quick_deletion
        val showLineNumber = Settings.show_line_numbers
        val cursorAnimation = Settings.cursor_animation
        val textSize = Settings.editor_text_size
        val wordWrap = Settings.word_wrap
        val keyboardSuggestion = Settings.show_suggestions
        val lineSpacing = Settings.line_spacing
        val renderWhitespace = Settings.render_whitespace

        props.deleteMultiSpaces = tabSize
        tabWidth = tabSize
        props.deleteEmptyLineFast = fastDelete
        props.stickyScroll = stickyScroll
        props.useICULibToSelectWords = true
        setPinLineNumber(pinLineNumber)
        isLineNumberEnabled = showLineNumber
        isCursorAnimationEnabled = cursorAnimation
        setTextSize(textSize.toFloat())
        isWordwrap = wordWrap
        lineSpacingMultiplier = lineSpacing
        isDisableSoftKbdIfHardKbdAvailable = Settings.hide_soft_keyboard_if_hardware
        showSuggestions(keyboardSuggestion)

        val minScaleSize: Float = 6f * resources.displayMetrics.scaledDensity
        val maxScaleSize: Float = 100f * resources.displayMetrics.scaledDensity
        setScaleTextSizes(minScaleSize, maxScaleSize)

        nonPrintablePaintingFlags = if (renderWhitespace) {
            FLAG_DRAW_LINE_SEPARATOR or
                    FLAG_DRAW_WHITESPACE_LEADING or
                    FLAG_DRAW_WHITESPACE_INNER or
                    FLAG_DRAW_WHITESPACE_TRAILING or
                    FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE or
                    FLAG_DRAW_WHITESPACE_IN_SELECTION
        } else 0
    }

    fun applyFont() {
        runCatching {
            val fontPath = Settings.selected_font_path
            val font = if (fontPath.isNotEmpty()) {
                FontCache.getFont(context, fontPath, Settings.is_selected_font_asset)
                    ?: FontCache.getFont(context, "fonts/Default.ttf", true)
            } else {
                FontCache.getFont(context, "fonts/Default.ttf", true)
            }

            typefaceText = font ?: Typeface.DEFAULT
            typefaceLineNumber = font ?: Typeface.DEFAULT
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

    companion object {
        private val completionFuture = CompletableDeferred<Unit>()

        private val colorSchemeCache = hashMapOf<String, TextMateColorScheme>()
        private val highlightingCache = hashMapOf<String, TextMateLanguage>()

        private fun getCacheKey(context: Context): String {
            val darkTheme = when (Settings.default_night_mode) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> isDarkMode(context)
            }

            // Important
            val prefix = if (darkTheme) "dark" else "light"
            return "${prefix}_${Settings.theme}_${Settings.amoled}"
        }

        suspend fun initGrammarRegistry() = withContext(Dispatchers.IO) {
            if (!completionFuture.isCompleted) {
                FileProviderRegistry.getInstance()
                    .addFileProvider(AssetsFileResolver(application!!.assets))
                GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")

                completionFuture.complete(Unit)
            }
        }
    }

    private val langMutex = Mutex()
    suspend fun setLanguage(languageScopeName: String) = withContext(Dispatchers.IO) {
        langMutex.withLock {

            while (!completionFuture.isCompleted) {
                delay(50)
            }

            delay(100)

            val language = highlightingCache.getOrPut(languageScopeName) {
                TextMateLanguage.create(languageScopeName, Settings.textmate_suggestion).apply {
                    if (Settings.textmate_suggestion) {
                        launch {
                            context.assets.open("textmate/keywords.json").use {
                                JsonParser.parseReader(InputStreamReader(it))
                                    .asJsonObject[languageScopeName]?.asJsonArray
                                    ?.map { el -> el.asString }
                                    ?.toTypedArray()
                                    ?.let(::setCompleterKeywords)
                            }
                        }
                    }
                }
            }

            language.useTab(Settings.actual_tabs)

            withContext(Dispatchers.Main) {
                setEditorLanguage(language as Language);
            }
        }
    }

    /**
     * Register an action button in the text action window.
     *
     * @param item The text action item instance to register.
     */
    fun registerTextAction(item: TextActionItem) {
        textActionWindow.registerTextAction(item)
    }

    /**
     * Unregister an action button in the text action window.
     *
     * @param item The text action item instance to unregister.
     */
    fun unregisterTextAction(item: TextActionItem) {
        textActionWindow.unregisterTextAction(item)
    }
}