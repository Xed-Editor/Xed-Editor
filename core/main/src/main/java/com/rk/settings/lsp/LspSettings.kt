package com.rk.settings.lsp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.components.InfoBlock
import com.rk.components.SettingsToggle
import com.rk.file.FileType
import com.rk.icons.Error
import com.rk.icons.XedIcons
import com.rk.lsp.lspRegistry
import com.rk.resources.getString
import com.rk.tabs.lsp_connections
import com.rk.utils.toast
import kotlin.collections.set

@Composable
fun LspSettings(modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }

    PreferenceLayout(label = stringResource(strings.lsp_settings), fab = {
        ExtendedFloatingActionButton(onClick = {
            showDialog = true
        }) {
            Icon(imageVector = Icons.Outlined.Add, null)
            Text(stringResource(strings.external_lsp))
        }
    }) {
        InfoBlock(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Info, contentDescription = null
                )
            },
            text = stringResource(strings.info_lsp),
        )

        InfoBlock(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Warning, contentDescription = null
                )
            },
            text = stringResource(strings.experimental_lsp),
            warning = true
        )

        if (lspRegistry.isNotEmpty()) {
            PreferenceGroup(heading = stringResource(strings.built_in)) {
                lspRegistry.forEach { server ->
                    SettingsToggle(
                        label = server.languageName,
                        default = Preference.getBoolean("lsp_${server.id}", false),
                        description = server.serverName,

                        showSwitch = true,
                        sideEffect = {
                            Preference.setBoolean("lsp_${server.id}", it)
                        }
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(strings.no_language_server))
            }
        }

        if (lsp_connections.isNotEmpty()) {
            PreferenceGroup(heading = stringResource(strings.external)) {
                lsp_connections.forEach { server ->
                    SettingsToggle(
                        label = server.key.joinToString(", ") { ".$it" },
                        default = true,
                        description = "${server.value.first}:${server.value.second}",
                        showSwitch = false,
                        endWidget = {
                            IconButton(onClick = {
                                lsp_connections.remove(server.key)
                            }) {
                                Icon(imageVector = Icons.Outlined.Delete, null)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))

        if (showDialog) {
            ExternalLSP(
                onDismiss = { showDialog = false },
                onConfirm = { host, port, extensions ->
                    runCatching {
                        lsp_connections[extensions] = Pair(host, port.toInt())
                    }.onFailure { toast(it.message) }
                }
            )
        }
    }
}

@Composable
private fun ExternalLSP(
    onDismiss: () -> Unit,
    onConfirm: (host: String, port: String, extensions: List<String>) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    var host by remember { mutableStateOf("localhost") }
    var port by remember { mutableStateOf("") }
    var extensions by remember { mutableStateOf("") }

    var hostError by remember { mutableStateOf<String?>(null) }
    var portError by remember { mutableStateOf<String?>(null) }
    var extensionsError by remember { mutableStateOf<String?>(null) }

    val confirmEnabled by remember {
        derivedStateOf {
            hostError == null &&
            portError == null &&
            extensionsError == null &&
            port.isNotBlank() &&
            extensions.isNotBlank()
        }
    }

    fun parseExtensions(input: String): List<String> {
        return input
            .split(",")
            .map { it.trim().trimStart('.') }
            .filter { it.isNotEmpty() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.external_lsp)) },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        hostError = null

                        if (host.isBlank()) {
                            hostError = strings.invalid_address.getString()
                        }
                    },
                    label = { Text(stringResource(strings.address)) },
                    singleLine = true,
                    isError = hostError != null,
                    supportingText = {
                        hostError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    trailingIcon = {
                        if (hostError != null) {
                            Icon(XedIcons.Error, "error", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = {
                        port = it
                        portError = null

                        val portInt = port.toIntOrNull()
                        if (port.isBlank() || portInt == null || portInt !in 0..65535) {
                            portError = strings.invalid_port.getString()
                        }
                    },
                    label = { Text(stringResource(strings.port_number)) },
                    modifier = Modifier.focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = portError != null,
                    supportingText = {
                        portError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    trailingIcon = {
                        if (portError != null) {
                            Icon(XedIcons.Error, "error", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                )

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                Spacer(Modifier.height(8.dp))
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedExtensions = parseExtensions(extensions)
                    onConfirm(host, port, parsedExtensions)
                    onDismiss()
                },
                enabled = confirmEnabled
            ) {
                Text(stringResource(strings.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(strings.cancel))
            }
        }
    )
}
