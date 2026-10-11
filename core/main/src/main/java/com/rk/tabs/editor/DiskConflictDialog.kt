package com.rk.tabs.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.rk.resources.strings

/**
 * Asks the user what to do when the file behind an editor tab was modified on disk since it was loaded or last saved.
 *
 * The dialog is not dismissed by tapping outside; back press counts as [onCancel].
 */
@Composable
fun DiskConflictDialog(fileName: String, onReload: () -> Unit, onOverwrite: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(strings.file_changed_on_disk)) },
        text = {
            Column {
                Text(text = fileName, style = MaterialTheme.typography.titleSmall)
                Text(text = stringResource(strings.file_changed_on_disk_desc))
            }
        },
        confirmButton = { TextButton(onClick = onOverwrite) { Text(stringResource(strings.overwrite)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text(stringResource(strings.cancel)) }
                TextButton(onClick = onReload) { Text(stringResource(strings.refresh)) }
            }
        },
    )
}
