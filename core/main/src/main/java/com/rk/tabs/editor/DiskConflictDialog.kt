package com.rk.tabs.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.resources.strings
import com.rk.utils.composableDialog

/**
 * Asks the user what to do when the file behind an editor tab was modified on disk since it was loaded or last saved.
 *
 * The dialog is not cancelable by tapping outside, so exactly one of the callbacks always runs.
 */
fun showDiskConflictDialog(fileName: String, onReload: () -> Unit, onOverwrite: () -> Unit, onCancel: () -> Unit) {
    composableDialog(cancelable = false) { alertDialog ->
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(strings.file_changed_on_disk),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = stringResource(strings.file_changed_on_disk_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        alertDialog?.dismiss()
                        onCancel()
                    }
                ) {
                    Text(stringResource(strings.cancel))
                }

                TextButton(
                    onClick = {
                        alertDialog?.dismiss()
                        onReload()
                    }
                ) {
                    Text(stringResource(strings.refresh))
                }

                TextButton(
                    onClick = {
                        alertDialog?.dismiss()
                        onOverwrite()
                    }
                ) {
                    Text(stringResource(strings.overwrite))
                }
            }
        }
    }
}
