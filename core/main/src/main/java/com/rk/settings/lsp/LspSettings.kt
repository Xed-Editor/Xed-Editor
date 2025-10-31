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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.components.InfoBlock
import com.rk.components.SettingsToggle
import com.rk.editor.textmateSources
import com.rk.lsp.lspRegistry
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
            Icon(imageVector = Icons.Outlined.Add,null)
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
                        description = server.supportedExtensions.joinToString(", ") {".$it"},
                        showSwitch = true,
                        sideEffect = {
                            Preference.setBoolean("lsp_${server.id}", it)
                        })
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(strings.no_language_server))
            }
        }

        if (lsp_connections.isNotEmpty()){
            lsp_connections.forEach { server ->
                PreferenceGroup(heading = stringResource(strings.external_lsp)) {
                    SettingsToggle(
                        label = server.key,
                        default = true,
                        description = "${server.value.first}:${server.value.second}",
                        showSwitch = false,
                        endWidget = {
                            IconButton(onClick = {
                                lsp_connections.remove(server.key)
                            }) {
                                Icon(imageVector = Icons.Outlined.Delete,null)
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
                onConfirm = { host, port, extension ->
                    runCatching {
                        if (textmateSources[extension] == null){
                            toast(strings.unsupported_file_ext)
                            return@runCatching
                        }
                        if (port.toIntOrNull() == null){
                            toast(strings.invalid_port)
                            return@runCatching
                        }
                        if (host.isBlank()){
                            toast(strings.invalid_address)
                            return@runCatching
                        }

                        lsp_connections[extension] = Pair(host,port.toInt())
                    }.onFailure {
                        toast(it.message)
                    }

                }
            )
        }
    }
}

@Composable
private fun ExternalLSP(
    onDismiss: () -> Unit,
    onConfirm: (host:String,port: String, extension: String) -> Unit
) {
    var host by remember { mutableStateOf("localhost") }
    var port by remember { mutableStateOf("") }
    var extension by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.external_lsp)) },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(strings.address)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(stringResource(strings.port_number)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = extension,
                    onValueChange = { extension = it },
                    label = { Text(stringResource(strings.file_ext_example)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(host,port, extension)
                onDismiss()
            }) {
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
