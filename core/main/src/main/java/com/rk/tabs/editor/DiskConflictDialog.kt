package com.rk.tabs.editor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings

/**
 * Asks the user what to do when the file behind an editor tab was modified on disk since it was loaded or last saved.
 */
@Composable
fun DiskConflictDialog(
    fileName: String,
    onReload: () -> Unit,
    onOverwrite: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(strings.file_changed_on_disk)) },
        text = { Text(stringResource(strings.file_changed_on_disk_desc).fillPlaceholders(fileName)) },
        confirmButton = { TextButton(onClick = onOverwrite) { Text(stringResource(strings.overwrite)) } },
        dismissButton = { TextButton(onClick = onReload) { Text(stringResource(strings.refresh)) } },
    )
}
