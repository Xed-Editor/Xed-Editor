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
                                contentDescription = "Connected",
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
                        when (server.status) {
                            LspConnectionStatus.CONNECTED -> "Status: Connected"
                            LspConnectionStatus.CONNECTING -> "Status: Connecting..."
                            LspConnectionStatus.ERROR -> "Status: Error"
                            LspConnectionStatus.NOT_RUNNING -> "Status: Not running"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = server.getConnectionColor() ?: MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = "Supported extensions: ${server.supportedExtensions.joinToString(", ") { ".$it" }}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PreferenceGroup(heading = "Features") {
            SettingsToggle(
                label = "Hover information",
                description = "Show documentation on hover",
                default = Preference.getBoolean("lsp_${server.id}_hover", true),
                sideEffect = { Preference.setBoolean("lsp_${server.id}_hover", it) },
            )

            SettingsToggle(
                label = "Signature help",
                description = "Show parameter information",
                default = Preference.getBoolean("lsp_${server.id}_signature_help", true),
                sideEffect = { Preference.setBoolean("lsp_${server.id}_signature_help", it) },
            )

            SettingsToggle(
                label = "Inlay hints",
                description = "Show inline type hints and parameter names",
                default = Preference.getBoolean("lsp_${server.id}_inlay_hints", true),
                sideEffect = { Preference.setBoolean("lsp_${server.id}_inlay_hints", it) },
            )

            SettingsToggle(
                label = "Code completion",
                description = "Enable auto-completion suggestions",
                default = Preference.getBoolean("lsp_${server.id}_completion", true),
                sideEffect = { Preference.setBoolean("lsp_${server.id}_completion", it) },
            )

            SettingsToggle(
                label = "Diagnostics",
                description = "Show errors and warnings",
                default = Preference.getBoolean("lsp_${server.id}_diagnostics", true),
                sideEffect = { Preference.setBoolean("lsp_${server.id}_diagnostics", it) },
            )

            SettingsToggle(
                label = "Code actions",
                description = "Enable quick fixes and refactorings",
                default = Preference.getBoolean("lsp_${server.id}_code_actions", true),
                sideEffect = { Preference.setBoolean("lsp_${server.id}_code_actions", it) },
            )

            SettingsToggle(
                label = "Formatting",
                description = "Enable document formatting",
                default = Preference.getBoolean("lsp_${server.id}_formatting", true),
                sideEffect = { Preference.setBoolean("lsp_${server.id}_formatting", it) },
            )
        }

        PreferenceGroup(heading = "Logs") {
            SettingsToggle(
                label = "Show logs",
                description = "Show the language server logs",
                default = false,
                showSwitch = false,
                onClick = { navController.navigate("lsp_server_logs/${server.id}") },
            )
        }
    }
}
