package com.rk.settings.lsp

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.icons.Error
import com.rk.icons.XedIcons
import com.rk.resources.getString
import com.rk.resources.strings

@Composable
fun ExternalProcessServer(dialogState: ExternalLspDialogState) {
    Text(
        modifier = Modifier.padding(8.dp),
        text = stringResource(strings.lsp_process_desc),
        style = MaterialTheme.typography.bodySmall,
    )

    OutlinedTextField(
        value = dialogState.lspCommand,
        onValueChange = {
            dialogState.lspCommand = it
            dialogState.externalError = null

            if (dialogState.lspCommand.isBlank()) {
                dialogState.externalError = strings.empty_command.getString()
            }
        },
        label = { Text(stringResource(strings.command)) },
        singleLine = true,
        isError = dialogState.externalError != null,
        supportingText =
            if (dialogState.externalError != null) {
                {
                    Text(
                        text = dialogState.externalError!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else null,
        trailingIcon =
            if (dialogState.externalError != null) {
                { Icon(XedIcons.Error, stringResource(strings.error), tint = MaterialTheme.colorScheme.error) }
            } else null,
    )

    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = dialogState.lspExtensions,
        onValueChange = { newValue -> dialogState.onExtensionsChange(newValue) },
        label = { Text(stringResource(strings.file_ext_example)) },
        singleLine = true,
        isError = dialogState.extensionsError != null,
        supportingText =
            if (dialogState.extensionsError != null) {
                {
                    Text(
                        text = dialogState.extensionsError!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else null,
        trailingIcon =
            if (dialogState.extensionsError != null) {
                { Icon(XedIcons.Error, stringResource(strings.error), tint = MaterialTheme.colorScheme.error) }
            } else null,
    )
}
