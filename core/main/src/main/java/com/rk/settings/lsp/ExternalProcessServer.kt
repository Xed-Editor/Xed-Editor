package com.rk.settings.lsp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rk.components.InfoBlock
import com.rk.file.FileType
import com.rk.icons.Error
import com.rk.icons.XedIcons
import com.rk.lsp.BaseLspServer
import com.rk.lsp.servers.ExternalProcessServer
import com.rk.lsp.servers.ExternalSocketServer
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.theme.onWarningSurface
import com.rk.theme.warningSurface
import com.rk.utils.toast

@Composable
fun ExternalProcessServer(modifier: Modifier = Modifier,onConfirm: (BaseLspServer) -> Unit,onDismiss:()-> Unit) {
    var command by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var extensionsError by remember { mutableStateOf<String?>(null) }
    var extensions by remember { mutableStateOf("") }

    val confirmEnabled by remember {
        derivedStateOf {
            error == null &&
            extensionsError == null &&
            command.isNotBlank() &&
            extensions.isNotBlank()
        }
    }

    fun parseExtensions(input: String): List<String> {
        return input
            .split(",")
            .map { it.trim().trimStart('.') }
            .filter { it.isNotEmpty() }
    }

    Text(
        modifier = Modifier.padding(8.dp),
        text = stringResource(strings.lsp_process_desc),
        style = MaterialTheme.typography.bodySmall
    )

    OutlinedTextField(
        value = command,
        onValueChange = {
            command = it
            error = null

            if (command.isBlank()) {
                error = strings.empty_command.getString()
            }
        },
        label = { Text(stringResource(strings.command)) },
        singleLine = true,
        isError = error != null,
        supportingText = {
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        trailingIcon = {
            if (error != null) {
                Icon(XedIcons.Error, "error", tint = MaterialTheme.colorScheme.error)
            }
        },
    )

    Spacer(Modifier.height(4.dp))

    OutlinedTextField(
        value = extensions,
        onValueChange = { newValue ->
            extensions = newValue
            extensionsError = null

            val parsedExtensions = parseExtensions(extensions)
            if (parsedExtensions.isEmpty()) {
                extensionsError = strings.unsupported_file_ext.getString()
            } else {
                val invalid = parsedExtensions.filter { !FileType.knowsExtension(it) }
                if (invalid.isNotEmpty()) {
                    extensionsError = "${strings.unsupported_file_ext.getString()}: ${invalid.joinToString(", ") { ".$it" }}"
                }
            }
        },
        label = { Text(stringResource(strings.file_ext_example)) },
        singleLine = true,
        isError = extensionsError != null,
        supportingText = {
            extensionsError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        trailingIcon = {
            if (extensionsError != null) {
                Icon(XedIcons.Error, "error", tint = MaterialTheme.colorScheme.error)
            }
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = {
            onDismiss()
        }) {
            Text(stringResource(strings.cancel))
        }

        Spacer(Modifier.width(8.dp))

        TextButton(onClick = {
            runCatching {
                val server = ExternalProcessServer(
                    languageName = parseExtensions(extensions).first(),
                    command = command,
                    supportedExtensions = parseExtensions(extensions)
                )
                onConfirm(server)
            }.onFailure { toast(it.message) }
            onDismiss()
        }, enabled = confirmEnabled) {
            Text(stringResource(strings.add))
        }
    }
}