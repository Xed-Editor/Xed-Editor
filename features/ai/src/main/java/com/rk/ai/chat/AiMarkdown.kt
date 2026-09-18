package com.rk.ai.chat

import android.graphics.Typeface
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.lsp.editor.text.SimpleMarkdownRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AiMarkdownText(markdown: String, color: Color, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryArgb = primary.toArgb()
    val selectionBackground = LocalTextSelectionColors.current.backgroundColor

    val spanned by
        produceState<Spanned?>(initialValue = null, markdown, primaryArgb) {
            value =
                withContext(Dispatchers.Default) {
                    runCatching {
                            SimpleMarkdownRenderer.renderAsync(
                                markdown = removeUnsupportedHtmlTags(markdown),
                                boldColor = primaryArgb,
                                inlineCodeColor = primaryArgb,
                                codeTypeface = Typeface.MONOSPACE,
                                linkColor = primaryArgb,
                            )
                        }
                        .getOrNull()
                }
        }

    val rendered = spanned
    if (rendered == null) {
        Text(
            text = markdown,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
    } else {
        AndroidView(
            factory = { context -> TextView(context) },
            update = { view ->
                view.text = rendered
                view.setTextColor(color.toArgb())
                view.textSize = BODY_TEXT_SIZE_SP
                view.includeFontPadding = false
                view.setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
                view.setTextIsSelectable(true)
                view.movementMethod = LinkMovementMethod.getInstance()
                view.highlightColor = selectionBackground.toArgb()
                view.isVerticalScrollBarEnabled = false
            },
            modifier = modifier.fillMaxWidth(),
        )
    }
}

internal fun removeUnsupportedHtmlTags(markdown: String): String {
    val result = StringBuilder()
    var lastIndex = 0

    protectedCodeRegex.findAll(markdown).forEach { match ->
        val before = markdown.substring(lastIndex, match.range.first)
        result.append(before.replace(unsupportedHtmlRegex, ""))

        result.append(match.value)

        lastIndex = match.range.last + 1
    }

    if (lastIndex < markdown.length) {
        result.append(markdown.substring(lastIndex).replace(unsupportedHtmlRegex, ""))
    }

    return result.toString()
}

private val protectedCodeRegex = Regex("(?s)(```.*?```|~~~.*?~~~|`[^`]*`|<code>.*?</code>)")
private val unsupportedHtmlRegex =
    Regex("(?is)<(?!/?(?:br|h[1-6]|blockquote|strong|em|code|pre|li|a|ul|ol|p)\\b)[^>]*>")

private const val BODY_TEXT_SIZE_SP = 14.5f
private const val LINE_SPACING_MULTIPLIER = 1.15f
