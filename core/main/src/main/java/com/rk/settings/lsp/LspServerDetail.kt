package com.rk.settings.lsp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceGroupHeading
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.lsp.BaseLspServer
import com.rk.lsp.BaseLspServerInstance
import com.rk.lsp.LspConnectionStatus
import com.rk.lsp.StatusIcon
import com.rk.lsp.getStatusColor
import com.rk.lsp.getStatusText
import com.rk.resources.drawables
import com.rk.resources.fillPlaceholders
import com.rk.resources.getQuantityString
import com.rk.resources.getString
import com.rk.resources.plurals
import com.rk.resources.strings
import com.rk.settings.Preference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LspServerDetail(navController: NavHostController, server: BaseLspServer) {
    PreferenceLayout(label = server.languageName) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp,
        ) {
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
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                val extensions = server.supportedExtensions.joinToString(", ") { ".$it" }
                Text(
                    text = stringResource(strings.supported_extensions).fillPlaceholders(extensions),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PreferenceGroupHeading(heading = stringResource(strings.instances))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (server.instances.isNotEmpty()) {
                server.instances.forEach { instance -> InstanceCard(instance, navController) }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 1.dp,
                ) {
                    SettingsToggle(
                        modifier = Modifier,
                        label = stringResource(strings.no_instances),
                        default = false,
                        sideEffect = {},
                        showSwitch = false,
                        startWidget = {},
                    )
                }
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
    }
}

@Composable
private fun InstanceCard(instance: BaseLspServerInstance, navController: NavHostController) {
    val scope = rememberCoroutineScope()

    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable(
                    onClick = { navController.navigate("lsp_server_logs/${instance.server.id}/${instance.id}") }
                ),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = instance.projectRoot.getName(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = instance.projectRoot.getAbsolutePath(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                instance.StatusIcon()
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(strings.status_info).fillPlaceholders(instance.getStatusText()),
                style = MaterialTheme.typography.bodyMedium,
                color = instance.getStatusColor() ?: MaterialTheme.colorScheme.onSurface,
            )

            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(1000)
                    now = System.currentTimeMillis()
                }
            }

            val uptime = timeAgo(now, instance.startupTime) ?: strings.offline.getString()
            Text(
                text = stringResource(strings.uptime).fillPlaceholders(uptime),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row {
                Button(onClick = { navController.navigate("lsp_server_logs/${instance.server.id}/${instance.id}") }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(painter = painterResource(drawables.eye), contentDescription = null)
                        Text(text = stringResource(strings.view_logs))
                    }
                }

                IconButton(onClick = { scope.launch { instance.restart() } }) {
                    Icon(
                        painter = painterResource(drawables.restart),
                        contentDescription = stringResource(strings.restart),
                    )
                }

                IconButton(
                    onClick = { scope.launch { instance.stop() } },
                    enabled = instance.status != LspConnectionStatus.NOT_RUNNING,
                ) {
                    Icon(painter = painterResource(drawables.stop), contentDescription = stringResource(strings.stop))
                }
            }
        }
    }
}

fun timeAgo(currentTimeMillis: Long, startTimeMillis: Long): String? {
    if (startTimeMillis == -1L) return null

    val diff = (currentTimeMillis - startTimeMillis)
    if (diff < 0) return null

    val seconds = (diff / 1000).toInt()
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    if (seconds < 1) return strings.time_just_now.getString()

    return when {
        seconds < 60 -> plurals.time_seconds_ago.getQuantityString(seconds, seconds)
        minutes < 60 -> plurals.time_minutes_ago.getQuantityString(minutes, minutes)
        hours < 24 -> plurals.time_hours_ago.getQuantityString(hours, hours)
        else -> plurals.time_days_ago.getQuantityString(days, days)
    }
}
