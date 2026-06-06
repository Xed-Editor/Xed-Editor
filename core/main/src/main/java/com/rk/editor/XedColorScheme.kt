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
        if (patchArgs != null) {
            applyPatchesTo(this, patchArgs)
        }
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

            colorScheme.setColor(HIGHLIGHTED_DELIMITERS_UNDERLINE, Color.TRANSPARENT)
            colorScheme.setColor(WHOLE_BACKGROUND, editorSurface)

            val containerColors = intArrayOf(
                TEXT_ACTION_WINDOW_BACKGROUND, COMPLETION_WND_BACKGROUND,
                DIAGNOSTIC_TOOLTIP_BACKGROUND, SIGNATURE_BACKGROUND,
                HOVER_BACKGROUND, LINE_NUMBER_PANEL
            )
            containerColors.forEach { colorScheme.setColor(it, surfaceContainer) }

            colorScheme.setColor(MINIMAP_BACKGROUND, setAlpha(surfaceContainer, 0.4f))
            colorScheme.setColor(MINIMAP_VIEWPORT, setAlpha(highSurfaceContainer, 0.6f))
            colorScheme.setColor(MINIMAP_VIEWPORT_BORDER, setAlpha(highSurfaceContainer, 0.6f))

            colorScheme.setColor(COMPLETION_WND_ITEM_CURRENT, highSurfaceContainer)

            val textColors = intArrayOf(
                TEXT_ACTION_WINDOW_ICON_COLOR, COMPLETION_WND_TEXT_PRIMARY,
                COMPLETION_WND_TEXT_SECONDARY, DIAGNOSTIC_TOOLTIP_BRIEF_MSG,
                DIAGNOSTIC_TOOLTIP_DETAILED_MSG, SIGNATURE_TEXT_NORMAL,
                HOVER_TEXT_NORMAL, LINE_NUMBER, LINE_NUMBER_CURRENT, LINE_NUMBER_PANEL_TEXT
            )
            textColors.forEach { colorScheme.setColor(it, onSurface) }

            colorScheme.setColor(SELECTION_HANDLE, handleColor)
            colorScheme.setColor(SELECTION_INSERT, selectionBg)
            colorScheme.setColor(MATCHED_TEXT_BACKGROUND, selectionBg)
            colorScheme.setColor(SELECTED_TEXT_BACKGROUND, selectionBg)

            val primaryColors = intArrayOf(
                HIGHLIGHTED_DELIMITERS_FOREGROUND, HIGHLIGHTED_DELIMITERS_BORDER,
                SIGNATURE_TEXT_HIGHLIGHTED_PARAMETER, HOVER_TEXT_HIGHLIGHTED,
                DIAGNOSTIC_TOOLTIP_ACTION, COMPLETION_WND_TEXT_MATCHED
            )
            primaryColors.forEach { colorScheme.setColor(it, colorPrimary) }

            colorScheme.setColor(BLOCK_LINE_CURRENT, setAlpha(onSurface, 0.6f))
            colorScheme.setColor(NON_PRINTABLE_CHAR, setAlpha(onSurface, 0.4f))
            colorScheme.setColor(BLOCK_LINE, setAlpha(onSurface, 0.4f))
            colorScheme.setColor(SCROLL_BAR_THUMB, setAlpha(onSurface, 0.3f))
            colorScheme.setColor(SCROLL_BAR_THUMB_PRESSED, setAlpha(onSurface, 0.2f))

            colorScheme.setColor(CURRENT_LINE, currentLine)
            colorScheme.setColor(LINE_NUMBER_BACKGROUND, gutterColor)

            val dividerColors = intArrayOf(
                LINE_DIVIDER, STICKY_SCROLL_DIVIDER,
                COMPLETION_WND_CORNER, SIGNATURE_BORDER, HOVER_BORDER
            )
            dividerColors.forEach { colorScheme.setColor(it, dividerColor) }

            colorScheme.setColor(PROBLEM_ERROR, errorColor)

            val editorColors =
                if (isDarkMode) currentTheme.value?.darkEditorColors
                else currentTheme.value?.lightEditorColors

            if (!editorColors.isNullOrEmpty()) {
                editorColors.forEach { colorScheme.setColor(it.key, it.color) }
            }
        }
    }
}
