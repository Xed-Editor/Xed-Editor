package com.rk.settings.lsp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.icons.Error
import com.rk.icons.XedIcons
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionStatus
import com.rk.lsp.getConnectionColor
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.theme.greenStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LspServerDetail(navController: NavHostController, server: BaseLspServer) {
    PreferenceLayout(label = server.languageName) {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    server.icon?.let {
                        Icon(
                            painter = painterResource(it),
                            contentDescription = null,
                            modifier = Modifier.size(42.dp).padding(end = 8.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = server.serverName)
                        Text(
                            text = "ID: ${server.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    when (server.status) {
                        LspConnectionStatus.CONNECTED -> {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = stringResource(strings.status_connected),
                                tint = MaterialTheme.colorScheme.greenStatus,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        LspConnectionStatus.CONNECTING -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                        LspConnectionStatus.ERROR -> {
                            Icon(
                                imageVector = XedIcons.Error,
                                contentDescription = stringResource(strings.error),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        LspConnectionStatus.NOT_RUNNING -> {}
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text =
                        stringResource(strings.status_info)
                            .fillPlaceholders(
                                when (server.status) {
                                    LspConnectionStatus.CONNECTED -> stringResource(strings.status_connected)
                                    LspConnectionStatus.CONNECTING -> stringResource(strings.status_connecting)
                                    LspConnectionStatus.ERROR -> stringResource(strings.error)
                                    LspConnectionStatus.NOT_RUNNING -> stringResource(strings.status_not_running)
                                }
                            ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = server.getConnectionColor() ?: MaterialTheme.colorScheme.onSurface,
                )

                val extensions = server.supportedExtensions.joinToString(", ") { ".$it" }
                Text(
                    text = stringResource(strings.supported_extensions).fillPlaceholders(extensions),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PreferenceGroup(heading = stringResource(strings.features)) {
            SettingsToggle(
                label = stringResource(strings.hover_information),
                description = stringResource(strings.hover_information_desc),
                default = Preference.getBoolean("lsp_${server.id}_hover", true),
                sideEffect = { Preference.setBoolean("lsp_${server.id}_hover", it) },
            )

            SettingsToggle(
                label = stringResource(strings.signature_help),
                description = stringResource(strings.signature_help_desc),
                default = Preference.getBoolean("lsp_${server.id}_signature_help", true),
                sideEffect = { Preference.setBoolean("lsp_${server.id}_signature_help", it) },
            )

            SettingsToggle(
                label = stringResource(strings.inlay_hints),
                description = stringResource(strings.inlay_hints_desc),
                default = Preference.getBoolean("lsp_${server.id}_inlay_hints", true),
                sideEffect = { Preference.setBoolean("lsp_${server.id}_inlay_hints", it) },
            )

            SettingsToggle(
                label = stringResource(strings.code_completion),
                description = stringResource(strings.code_completion_desc),
                default = Preference.getBoolean("lsp_${server.id}_completion", true),
                sideEffect = { Preference.setBoolean("lsp_${server.id}_completion", it) },
            )

            SettingsToggle(
                label = stringResource(strings.diagnostics),
                description = stringResource(strings.diagnostics_desc),
                default = Preference.getBoolean("lsp_${server.id}_diagnostics", true),
                sideEffect = { Preference.setBoolean("lsp_${server.id}_diagnostics", it) },
            )

            SettingsToggle(
                label = stringResource(strings.formatting),
                description = stringResource(strings.formatting_desc),
                default = Preference.getBoolean("lsp_${server.id}_formatting", true),
                sideEffect = { Preference.setBoolean("lsp_${server.id}_formatting", it) },
            )
        }

        PreferenceGroup(heading = stringResource(strings.logs)) {
            SettingsToggle(
                label = stringResource(strings.view_logs),
                description = stringResource(strings.view_logs_desc),
                default = false,
                showSwitch = false,
                onClick = { navController.navigate("lsp_server_logs/${server.id}") },
            )
        }
    }
}
