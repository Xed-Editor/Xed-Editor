package com.rk.settings.lsp

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rk.icons.Error
import com.rk.icons.XedIcons
import com.rk.resources.getString
import com.rk.resources.strings

@Composable
fun ExternalSocketServer(dialogState: ExternalLspDialogState) {
    OutlinedTextField(
        value = dialogState.lspHost,
        onValueChange = {
            dialogState.lspHost = it
            dialogState.hostError = null

            if (dialogState.lspHost.isBlank()) {
                dialogState.hostError = strings.invalid_address.getString()
            }
        },
        label = { Text(stringResource(strings.address)) },
        singleLine = true,
        isError = dialogState.hostError != null,
        supportingText =
            if (dialogState.hostError != null) {
                {
                    Text(
                        text = dialogState.hostError!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else null,
        trailingIcon =
            if (dialogState.hostError != null) {
                { Icon(XedIcons.Error, stringResource(strings.error), tint = MaterialTheme.colorScheme.error) }
            } else null,
    )

    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = dialogState.lspPort,
        onValueChange = {
            dialogState.lspPort = it
            dialogState.portError = null

            val portInt = dialogState.lspPort.toIntOrNull()
            if (dialogState.lspPort.isBlank() || portInt == null || portInt !in 0..65535) {
                dialogState.portError = strings.invalid_port.getString()
            }
        },
        label = { Text(stringResource(strings.port_number)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = dialogState.portError != null,
        supportingText =
            if (dialogState.portError != null) {
                {
                    Text(
                        text = dialogState.portError!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else null,
        trailingIcon =
            if (dialogState.portError != null) {
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
