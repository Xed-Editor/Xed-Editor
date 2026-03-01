package com.rk.editor

import android.graphics.Color
import com.rk.theme.currentTheme
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class XedColorScheme(
    private val patchArgs: Editor.PatchArgs?,
    themeModel: ThemeModel,
    themeRegistry: ThemeRegistry = ThemeRegistry.getInstance(),
) : TextMateColorScheme(themeRegistry, themeModel) {

    init {
        themeRegistry.loadTheme(themeModel)
    }

    override fun applyDefault() {
        super.applyDefault()
        applyPatches()
    }

    private fun applyPatches() {
        if (patchArgs == null) return
        applyPatchesTo(this, patchArgs)
    }

    companion object {
        private fun setAlpha(color: Int, factor: Float): Int {
            val a = Color.alpha(color)
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)

            val newAlpha = (a * factor).toInt().coerceIn(0, 255)
            return Color.argb(newAlpha, r, g, b)
        }

        fun applyPatchesTo(colorScheme: EditorColorScheme, patchArgs: Editor.PatchArgs) {
            val (
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
            ) = patchArgs

            fun setColors(color: Int, vararg types: Int) {
                types.forEach { colorScheme.setColor(it, color) }
            }

            setColors(Color.TRANSPARENT, HIGHLIGHTED_DELIMITERS_UNDERLINE)

            setColors(editorSurface, WHOLE_BACKGROUND)

            setColors(
                surfaceContainer,
                TEXT_ACTION_WINDOW_BACKGROUND,
                COMPLETION_WND_BACKGROUND,
                DIAGNOSTIC_TOOLTIP_BACKGROUND,
                SIGNATURE_BACKGROUND,
                HOVER_BACKGROUND,
                LINE_NUMBER_PANEL,
            )

            setColors(highSurfaceContainer, COMPLETION_WND_ITEM_CURRENT)

            setColors(
                onSurface,
                TEXT_ACTION_WINDOW_ICON_COLOR,
                COMPLETION_WND_TEXT_PRIMARY,
                COMPLETION_WND_TEXT_SECONDARY,
                DIAGNOSTIC_TOOLTIP_BRIEF_MSG,
                DIAGNOSTIC_TOOLTIP_DETAILED_MSG,
                SIGNATURE_TEXT_NORMAL,
                HOVER_TEXT_NORMAL,
                LINE_NUMBER,
                LINE_NUMBER_CURRENT,
            )

            setColors(handleColor, SELECTION_HANDLE)
            setColors(selectionBg, SELECTION_INSERT, MATCHED_TEXT_BACKGROUND, SELECTED_TEXT_BACKGROUND)
            setColors(
                colorPrimary,
                HIGHLIGHTED_DELIMITERS_FOREGROUND,
                HIGHLIGHTED_DELIMITERS_BORDER,
                SIGNATURE_TEXT_HIGHLIGHTED_PARAMETER,
                HOVER_TEXT_HIGHLIGHTED,
                DIAGNOSTIC_TOOLTIP_ACTION,
                COMPLETION_WND_TEXT_MATCHED,
            )

            setColors(setAlpha(onSurface, 0.6f), BLOCK_LINE_CURRENT)
            setColors(setAlpha(onSurface, 0.4f), NON_PRINTABLE_CHAR, BLOCK_LINE)
            setColors(setAlpha(onSurface, 0.3f), SCROLL_BAR_THUMB)
            setColors(setAlpha(onSurface, 0.2f), SCROLL_BAR_THUMB_PRESSED)

            setColors(currentLine, CURRENT_LINE)
            setColors(gutterColor, LINE_NUMBER_BACKGROUND)
            setColors(
                dividerColor,
                LINE_DIVIDER,
                STICKY_SCROLL_DIVIDER,
                COMPLETION_WND_CORNER,
                SIGNATURE_BORDER,
                HOVER_BORDER,
            )

            setColors(errorColor, PROBLEM_ERROR)

            val editorColors =
                if (isDarkMode) {
                    currentTheme.value?.darkEditorColors
                } else {
                    currentTheme.value?.lightEditorColors
                }

            if (editorColors.isNullOrEmpty().not()) {
                editorColors.forEach { colorScheme.setColor(it.key, it.color) }
            }
        }
    }
}
