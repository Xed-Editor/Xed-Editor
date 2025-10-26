package com.rk.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings

data class CodeItem(
    val snippet: AnnotatedString,
    val fileName: String,
    val line: Int,
    val column: Int,
    val onClick: () -> Unit
)

@Composable
fun FindingsDialog(
    title: String,
    description: String,
    codeItems: List<CodeItem>,
    onFinish: () -> Unit
) {
    val grouped = codeItems.groupBy { it.fileName }

    AlertDialog(
        onDismissRequest = onFinish,
        title = {
            Text(title)
        },
        text = {
            Column {
                Text(description.fillPlaceholders(codeItems.size))
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn {
                    grouped.forEach { (fileName, codeItems) ->
                        item {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        items(codeItems) { item ->
                            CodeItemRow(item = item, onClick = {
                                item.onClick()
                                onFinish()
                            })
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onFinish
            ) {
                Text(stringResource(id = strings.cancel))
            }
        }
    )
}

@Composable
fun CodeItemRow(
    item: CodeItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.snippet,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${item.line}:${item.column}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}