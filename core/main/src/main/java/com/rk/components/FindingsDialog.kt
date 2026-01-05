package com.rk.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings

@Composable
fun FindingsDialog(title: String, description: String, codeItems: List<CodeItem>, onFinish: () -> Unit) {
    val grouped = codeItems.groupBy { it.fileName }

    AlertDialog(
        onDismissRequest = onFinish,
        title = { Text(title) },
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
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }

                        items(codeItems) { item ->
                            CodeItemRow(
                                item = item,
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
