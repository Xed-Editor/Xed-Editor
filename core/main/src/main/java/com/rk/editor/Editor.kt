package com.rk.editor

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.rk.settings.Settings
import com.rk.settings.editor.LineEnding
import com.rk.utils.errorDialog
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.TextActionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.ec4j.core.ResourceProperties
import org.ec4j.core.model.PropertyType

@Suppress("NOTHING_TO_INLINE")
class Editor : CodeEditor {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val scope = CoroutineScope(Dispatchers.Default)

    var lineEnding = LineEnding.LF
    var insertFinalNewline = false
    var trimTrailingWhitespace = false

    data class PatchArgs(
        val isDarkMode: Boolean,
        val editorSurface: Int,
        val surfaceContainer: Int,
        val highSurfaceContainer: Int,
        val onSurface: Int,
        val colorPrimary: Int,
        val selectionBg: Int,
        val handleColor: Int,
        val gutterColor: Int,
        val currentLine: Int,
        val dividerColor: Int,
        val errorColor: Int,
    )

    init {
        applyFont()
        applySettings()

        getComponent(EditorAutoCompletion::class.java).setEnabledAnimation(true)
    }

    fun setThemeColors(isDarkMode: Boolean, selectionColors: TextSelectionColors, colorScheme: ColorScheme) {
        val surfaceColor = if (isDarkMode) colorScheme.surfaceDim else colorScheme.surface
        val surfaceContainer = colorScheme.surfaceContainer
        val highSurfaceContainer = colorScheme.surfaceContainerHigh
        val onSurfaceColor = colorScheme.onSurface
        val colorPrimary = colorScheme.primary
        val divider = colorScheme.outlineVariant
        val errorColor = colorScheme.error

        val selectionBackground = selectionColors.backgroundColor
        val handleColor = selectionColors.handleColor

        val gutterColor = colorScheme.surfaceColorAtElevation(1.dp)
        val currentLineColor = colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.8f)

        setThemeColors(
            isDarkMode = isDarkMode,
            editorSurface = surfaceColor.toArgb(),
            surfaceContainer = surfaceContainer.toArgb(),
            highSurfaceContainer = highSurfaceContainer.toArgb(),
            onSurface = onSurfaceColor.toArgb(),
            colorPrimary = colorPrimary.toArgb(),
            selectionBg = selectionBackground.toArgb(),
            handleColor = handleColor.toArgb(),
            gutterColor = gutterColor.toArgb(),
            currentLine = currentLineColor.toArgb(),
            dividerColor = divider.toArgb(),
            errorColor = errorColor.toArgb(),
        )
    }

    fun setThemeColors(
        isDarkMode: Boolean,
        editorSurface: Int,
        surfaceContainer: Int,
        highSurfaceContainer: Int,
        onSurface: Int,
        colorPrimary: Int,
        selectionBg: Int,
        handleColor: Int,
        gutterColor: Int,
        currentLine: Int,
        dividerColor: Int,
        errorColor: Int,
    ) {
        val patchArgs =
            PatchArgs(
                isDarkMode,
                editorSurface,
                surfaceContainer,
                highSurfaceContainer,
                onSurface,
                colorPrimary,
                selectionBg,
                handleColor,
                gutterColor,
                currentLine,
                dividerColor,
                errorColor,
            )

        XedColorScheme.applyPatchesTo(colorScheme, patchArgs) // pre-apply patches
        scope.launch {
            // TextMate color scheme with patches
            val createdColorScheme = ThemeManager.createColorScheme(context, patchArgs)
            withContext(Dispatchers.Main) { colorScheme = createdColorScheme }
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
        val hideSoftKbd = Settings.hide_soft_keyboard_if_hardware
        val lineEndingSetting = Settings.line_ending
        val finalNewline = Settings.insert_final_newline
        val trailingWhitespace = Settings.trim_trailing_whitespace
        val completeOnEnter = Settings.complete_on_enter

        props.deleteMultiSpaces = tabSize
        tabWidth = tabSize
        props.deleteEmptyLineFast = fastDelete
        props.stickyScroll = stickyScroll
        props.useICULibToSelectWords = true
        props.selectCompletionItemOnEnterForSoftKbd = completeOnEnter
        setPinLineNumber(pinLineNumber)
        isLineNumberEnabled = showLineNumber
        isCursorAnimationEnabled = cursorAnimation
        setTextSize(textSize.toFloat())
        setWordwrap(wordWrap, true, true)
        lineSpacingMultiplier = lineSpacing
        isDisableSoftKbdIfHardKbdAvailable = hideSoftKbd
        showSuggestions(keyboardSuggestion)

        LineEnding.fromValue(lineEndingSetting)?.let { lineEnding = it }
        lineSeparator = lineEnding.type
        insertFinalNewline = finalNewline
        trimTrailingWhitespace = trailingWhitespace

        val minScaleSize: Float = 6f * resources.displayMetrics.scaledDensity
        val maxScaleSize: Float = 100f * resources.displayMetrics.scaledDensity
        setScaleTextSizes(minScaleSize, maxScaleSize)

        nonPrintablePaintingFlags =
            if (renderWhitespace) {
                FLAG_DRAW_LINE_SEPARATOR or
                    FLAG_DRAW_WHITESPACE_LEADING or
                    FLAG_DRAW_WHITESPACE_INNER or
                    FLAG_DRAW_WHITESPACE_TRAILING or
                    FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE or
                    FLAG_DRAW_WHITESPACE_IN_SELECTION
            } else 0

        lineNumberMarginLeft = 9f
    }

    fun applySettings(resourceProperties: ResourceProperties) {
        val indentStyle: PropertyType.IndentStyleValue? =
            resourceProperties.getValue(PropertyType.indent_style, null, false)
        val indentSize: Int? = resourceProperties.getValue(PropertyType.indent_size, null, false)
        val tabSize: Int? = resourceProperties.getValue(PropertyType.tab_width, null, false)

        indentStyle?.let {
            val useTabs = it == PropertyType.IndentStyleValue.tab
            (editorLanguage as? TextMateLanguage)?.useTab(useTabs)
        }

        val actualTabSize = indentSize ?: tabSize
        actualTabSize?.let {
            props.deleteMultiSpaces = it
            tabWidth = it
        }

        val endOfLine: PropertyType.EndOfLineValue? = resourceProperties.getValue(PropertyType.end_of_line, null, false)
        lineEnding =
            when (endOfLine) {
                PropertyType.EndOfLineValue.cr -> LineEnding.CR
                PropertyType.EndOfLineValue.lf -> LineEnding.LF
                PropertyType.EndOfLineValue.crlf -> LineEnding.CRLF
                else -> lineEnding
            }
        lineSeparator = lineEnding.type

        insertFinalNewline = resourceProperties.getValue(PropertyType.insert_final_newline, insertFinalNewline, false)
        trimTrailingWhitespace =
            resourceProperties.getValue(PropertyType.trim_trailing_whitespace, trimTrailingWhitespace, false)
    }

    fun applyFont() {
        runCatching {
                val fontPath = Settings.selected_font_path
                val font =
                    if (fontPath.isNotEmpty()) {
                        FontCache.getFont(context, fontPath, Settings.is_selected_font_asset)
                            ?: FontCache.getFont(context, "fonts/Default.ttf", true)
                    } else {
                        FontCache.getFont(context, "fonts/Default.ttf", true)
                    }

                typefaceText = font ?: Typeface.DEFAULT
                typefaceLineNumber = font ?: Typeface.DEFAULT
            }
            .onFailure { errorDialog(it) }
    }

    fun showSuggestions(yes: Boolean) {
        inputType =
            if (yes) {
                InputType.TYPE_TEXT_VARIATION_NORMAL
            } else {
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }
    }

    private val langMutex = Mutex()

    suspend fun setLanguage(textmateScope: String) {
        langMutex.withLock {
            val language = LanguageManager.createLanguage(context, textmateScope)
            language.useTab(Settings.actual_tabs)

            if (Settings.textmate_suggestions) {
                val keywords = KeywordManager.getKeywords(textmateScope)
                keywords?.let { language.setCompleterKeywords(it.toTypedArray()) }
            }

            withContext(Dispatchers.Main) { setEditorLanguage(language) }
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

    /**
     * Retrieves the currently selected text in the editor.
     *
     * @return The selected text or `null` if no text is currently selected.
     */
    fun getSelectedText(): String? {
        if (!isTextSelected) return null

        val selectionStart = cursorRange.startIndex
        val selectionEnd = cursorRange.endIndex
        return text.substring(selectionStart, selectionEnd)
    }
}
