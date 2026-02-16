package com.rk.search

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.rk.activities.main.MainViewModel
import com.rk.file.FileObject
import com.rk.tabs.editor.EditorTab
import com.rk.theme.currentTheme
import com.rk.utils.getSelectionColor
import com.rk.utils.isDarkTheme
import com.rk.utils.toAnnotatedString
import io.github.rosemoe.sora.lsp.editor.text.MarkdownCodeHighlighterRegistry
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Range

class SnippetBuilder(private val context: Context) {
    /**
     * Generates a text portion of the provided text line that contains the range.
     *
     * The text of the range is printed with the selection color. The code is highlighted with the help of the
     * [MarkdownCodeHighlighterRegistry].
     *
     * @return A [Pair] containing the [AnnotatedString] and the start index of the highlighted text.
     */
    suspend fun generateSnippet(
        text: String,
        highlightStart: Int,
        highlightEnd: Int,
        fileExt: String,
    ): Pair<AnnotatedString, Int> {
        return withContext(Dispatchers.Default) {
            val trimmedTargetLine = text.trim()
            val leadingWhitespace = text.indexOf(trimmedTargetLine)

            val rangeStartTrimmed = highlightStart - leadingWhitespace
            val rangeEndTrimmed = highlightEnd - leadingWhitespace

            val highlightedSpanned =
                MarkdownCodeHighlighterRegistry.global.highlightAsync(
                    code = trimmedTargetLine,
                    language = fileExt,
                    codeTypeface = Typeface.MONOSPACE,
                )

            val highlightedAnnotated = (highlightedSpanned as? Spannable)?.toAnnotatedString() ?: highlightedSpanned

            val editorColors =
                if (isDarkTheme(context)) {
                    currentTheme.value?.darkEditorColors
                } else {
                    currentTheme.value?.lightEditorColors
                }
            val selectionColor =
                editorColors?.find { it.key == EditorColorScheme.SELECTED_TEXT_BACKGROUND }?.color?.let { Color(it) }
                    ?: getSelectionColor()

            buildAnnotatedString {
                append(highlightedAnnotated)
                addStyle(
                    style = SpanStyle(background = selectionColor),
                    start = rangeStartTrimmed,
                    end = rangeEndTrimmed,
                )
            } to rangeStartTrimmed
        }
    }

    /**
     * Generates a text portion of the line in the provided file that contains the range.
     *
     * The text of the range is printed with the selection color. The code is highlighted with the help of the
     * [MarkdownCodeHighlighterRegistry].
     *
     * @return A [Pair] containing the [AnnotatedString] and the start index of the highlighted text.
     */
    suspend fun generateLspSnippet(
        viewModel: MainViewModel,
        targetFile: FileObject,
        range: Range,
    ): Pair<AnnotatedString, Int> {
        return withContext(Dispatchers.IO) {
            val openedTab = viewModel.tabs.find { it is EditorTab && it.file == targetFile } as? EditorTab

            // Only read file if it's not already opened as a tab
            val lines =
                if (openedTab != null) {
                    openedTab.editorState.editor.get()?.text.toString().lines()
                } else {
                    targetFile.readText()?.lines() ?: emptyList()
                }

            val targetLine = lines[range.start.line]
            val trimmedTargetLine = targetLine.trim()
            val leadingWhitespace = targetLine.indexOf(trimmedTargetLine)

            val rangeStartTrimmed = range.start.character - leadingWhitespace
            val rangeEndTrimmed = range.end.character - leadingWhitespace

            val fileExt = targetFile.getName().substringAfterLast(".", "")

            val highlightedSpanned =
                MarkdownCodeHighlighterRegistry.global.highlightAsync(
                    code = trimmedTargetLine,
                    language = fileExt,
                    codeTypeface = Typeface.MONOSPACE,
                )

            val highlightedAnnotated = (highlightedSpanned as? Spannable)?.toAnnotatedString() ?: highlightedSpanned

            val editorColors =
                if (isDarkTheme(context)) {
                    currentTheme.value?.darkEditorColors
                } else {
                    currentTheme.value?.lightEditorColors
                }
            val selectionColor =
                editorColors?.find { it.key == EditorColorScheme.SELECTED_TEXT_BACKGROUND }?.color?.let { Color(it) }
                    ?: getSelectionColor()

            buildAnnotatedString {
                append(highlightedAnnotated)
                addStyle(
                    style = SpanStyle(background = selectionColor),
                    start = rangeStartTrimmed,
                    end = rangeEndTrimmed,
                )
            } to rangeStartTrimmed
        }
    }
}
