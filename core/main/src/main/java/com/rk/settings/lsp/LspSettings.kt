package com.rk.settings.lsp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.components.InfoBlock
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspPersistence
import com.rk.lsp.builtInServer
import com.rk.lsp.externalServers
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.utils.toast

@Composable
fun LspSettings(modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }

    PreferenceLayout(
        label = stringResource(strings.manage_language_servers),
        fab = {
            ExtendedFloatingActionButton(onClick = { showDialog = true }) {
                Icon(imageVector = Icons.Outlined.Add, null)
                Text(stringResource(strings.external_lsp))
            }
        },
    ) {
        InfoBlock(
            icon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
            text = stringResource(strings.info_lsp),
        )

        InfoBlock(
            icon = { Icon(imageVector = Icons.Outlined.Warning, contentDescription = null) },
            text = stringResource(strings.experimental_lsp),
            warning = true,
        )

        if (builtInServer.isNotEmpty()) {
            PreferenceGroup(heading = stringResource(strings.built_in)) {
                builtInServer.forEach { server ->
                    SettingsToggle(
                        label = server.languageName,
                        default = Preference.getBoolean("lsp_${server.id}", false),
                        description = server.serverName,
                        showSwitch = true,
                        onClick = { toast("yayy!") },
                        startWidget =
                            server.icon?.let {
                                {
                                    Icon(
                                        modifier = Modifier.padding(start = 16.dp),
                                        painter = painterResource(it),
                                        contentDescription = null,
                                    )
                                }
                            },
                        sideEffect = { Preference.setBoolean("lsp_${server.id}", it) },
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(strings.no_language_server))
            }
        }

        if (externalServers.isNotEmpty()) {
            PreferenceGroup(heading = stringResource(strings.external)) {
                externalServers.forEach { server ->
                    SettingsToggle(
                        label = server.languageName,
                        default = true,
                        description = server.serverName,
                        showSwitch = false,
                        onClick = { toast("yayy!") },
                        startWidget =
                            server.icon?.let {
                                {
                                    Icon(
                                        modifier = Modifier.padding(start = 16.dp),
                                        painter = painterResource(it),
                                        contentDescription = null,
                                    )
                                }
                            },
                        endWidget = {
                            IconButton(
                                onClick = {
                                    externalServers.remove(server)
                                    LspPersistence.saveServers()
                                }
                            ) {
                                Icon(imageVector = Icons.Outlined.Delete, null)
                            }
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))

        if (showDialog) {
            ExternalLSP(
                onDismiss = { showDialog = false },
                onConfirm = { server ->
                    externalServers.add(server)
                    LspPersistence.saveServers()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalLSP(onDismiss: () -> Unit, onConfirm: (BaseLspServer) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.external_lsp)) },
        confirmButton = {},
        text = {
            Column {
                val socketLabel = stringResource(strings.socket)
                val processLabel = stringResource(strings.process)
                var selected by remember { mutableStateOf(socketLabel) }
                val options = listOf(socketLabel, processLabel)

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEach { option ->
                        SegmentedButton(
                            selected = selected == option,
                            onClick = { selected = option },
                            label = { Text(option) },
                            shape =
                                SegmentedButtonDefaults.itemShape(index = options.indexOf(option), count = options.size),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                when (selected) {
                    socketLabel -> ExternalSocketServer(onConfirm = onConfirm, onDismiss = { onDismiss() })
                    processLabel -> ExternalProcessServer(onConfirm = onConfirm, onDismiss = { onDismiss() })
                }
            }
        },
    )
}
