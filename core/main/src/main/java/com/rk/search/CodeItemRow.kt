package com.rk.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.components.compose.utils.addIf
import com.rk.file.FileObject
import kotlinx.coroutines.launch

data class CodeItem(
    val snippet: Snippet,
    val file: FileObject,
    val isHidden: Boolean = false,
    val line: Int,
    val column: Int,
    val isOpen: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun CodeItemRow(
    item: CodeItem,
    onClick: () -> Unit,
    paddingValues: PaddingValues = PaddingValues(start = 16.dp),
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()

    Row(
        modifier =
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(paddingValues).addIf(item.isHidden) {
                alpha(0.5f)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val style = MaterialTheme.typography.bodySmall
        val layoutResult = textMeasurer.measure(text = "000", style = style)

        val widthInDp = with(density) { layoutResult.size.width.toDp() }

        Text(
            text = "${item.line + 1}",
            style = style,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.width(widthInDp),
        )
        Spacer(modifier = Modifier.width(4.dp))

        val scrollState = rememberScrollState()
        Row(modifier = Modifier.weight(1f).padding(vertical = 8.dp).horizontalScroll(scrollState)) {
            Text(
                text = item.snippet.text,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = style,
                onTextLayout = { layoutResult ->
                    val highlightStart = item.snippet.highlight.startIndex
                    if (highlightStart < layoutResult.layoutInput.text.length) {
                        val box = layoutResult.getBoundingBox(highlightStart)
                        val targetScroll = (box.left - 16f).toInt().coerceAtLeast(0)
                        if (scrollState.value == 0) {
                            scope.launch { scrollState.scrollTo(targetScroll) }
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        leadingIcon?.let {
            leadingIcon()
            Spacer(modifier = Modifier.width(16.dp))
        }
    }
}
