package com.rk.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.components.compose.utils.addIf
import com.rk.filetree.FileIcon
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings
import com.rk.utils.rememberNumberFormatter

@Composable
fun FindingsDialog(title: String, description: String, codeItems: List<CodeItem>, onFinish: () -> Unit) {
    val grouped = codeItems.groupBy { it.file }

    val numberFormatter = rememberNumberFormatter()
    val resultCount by remember { derivedStateOf { numberFormatter.format(codeItems.size) } }

    AlertDialog(
        onDismissRequest = onFinish,
        title = { Text(title) },
        text = {
            Column {
                Text(description.fillPlaceholders(resultCount))
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn {
                    grouped.forEach { (fileObject, codeItems) ->
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier.addIf(codeItems.first().isHidden) { alpha(0.5f) }
                                        .padding(top = 8.dp, bottom = 4.dp),
                            ) {
                                FileIcon(file = fileObject, iconTint = MaterialTheme.colorScheme.primary)

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text =
                                        if (codeItems.first().isOpen) {
                                            stringResource(strings.file_name_opened)
                                                .fillPlaceholders(fileObject.getName())
                                        } else {
                                            fileObject.getName()
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        items(codeItems) { item ->
                            CodeItemRow(
                                item = item,
                                paddingValues = PaddingValues(),
                                onClick = {
                                    item.onClick()
                                    onFinish()
                                },
                            )
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onFinish) { Text(stringResource(id = strings.cancel)) } },
    )
}
