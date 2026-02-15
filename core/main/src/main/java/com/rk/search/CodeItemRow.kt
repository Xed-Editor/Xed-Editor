package com.rk.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.components.compose.utils.addIf
import com.rk.file.FileObject
import kotlinx.coroutines.launch

data class CodeItem(
    val snippet: AnnotatedString,
    val file: FileObject,
    val isHidden: Boolean = false,
    val highlightStart: Int = 0,
    val line: Int,
    val column: Int,
    val opened: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun CodeItemRow(item: CodeItem, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()

    Row(
        modifier =
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp).addIf(
                item.isHidden
            ) {
                alpha(0.5f)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val scrollState = rememberScrollState()
        Row(modifier = Modifier.weight(1f).horizontalScroll(scrollState)) {
            Text(
                text = item.snippet,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                onTextLayout = { layoutResult ->
                    if (item.highlightStart < layoutResult.layoutInput.text.length) {
                        val box = layoutResult.getBoundingBox(item.highlightStart)
                        val targetScroll = (box.left - 16f).toInt().coerceAtLeast(0)
                        if (scrollState.value == 0) {
                            scope.launch { scrollState.scrollTo(targetScroll) }
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${item.line}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}
