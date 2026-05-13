package com.rk.settings.lsp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavHostController
import com.rk.activities.settings.snackbarHostStateRef
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceGroupHeading
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.lsp.DefinitionPrevention
import com.rk.lsp.LspConnectionStatus
import com.rk.lsp.LspServer
import com.rk.resources.drawables
import com.rk.resources.fillPlaceholders
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class LspInstallationAction {
    UPDATE,
    INSTALL,
    UNINSTALL,
    LOADING,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LspServerDetail(navController: NavHostController, server: LspServer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            refreshKey++
        }
    }

    @Composable
    fun RestartAllButton(enabled: Boolean) {
        Button(enabled = enabled, onClick = { scope.launch { server.restartAllInstances() } }) {
            Icon(painter = painterResource(drawables.restart), contentDescription = stringResource(strings.restart))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(strings.restart_all))
        }
    }

    @Composable
    fun UninstallButton() {
        if (!server.canBeUninstalled) return

        FilledTonalButton(
            onClick = { server.uninstall(context) },
            colors =
                ButtonDefaults.filledTonalButtonColors()
                    .copy(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
        ) {
            Icon(imageVector = Icons.Outlined.Delete, contentDescription = stringResource(strings.uninstall))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(strings.uninstall))
        }
    }

    @Composable
    fun UpdateButton() {
        FilledTonalButton(onClick = { server.update(context) }) {
            Icon(painter = painterResource(drawables.update), contentDescription = stringResource(strings.update))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(strings.update))
        }
    }

    @Composable
    fun DownloadButton() {
        FilledTonalButton(onClick = { server.install(context) }) {
            Icon(painter = painterResource(drawables.download), contentDescription = stringResource(strings.download))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(strings.install))
        }
    }

    @Composable
    fun LspFeatureToggle(label: String, description: String? = null, preferenceId: String, server: LspServer) {
        SettingsToggle(
            label = label,
            description = description,
            default = Preference.getBoolean(preferenceId, true),
            sideEffect = {
                Preference.setBoolean(preferenceId, it)
                showRestartRequirement(scope, server)
            },
        )
    }

    PreferenceLayout(
        label = server.languageName,
        snackbarHost = { snackbarHostStateRef.get()?.let { SnackbarHost(hostState = it) } },
    ) {
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

        val status by rememberLspInstallStatus(context, server, refreshKey)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            val hasRunningInstances = server.instances.map { it.status }.contains(LspConnectionStatus.RUNNING)
            RestartAllButton(hasRunningInstances)

            when (status) {
                LspInstallationAction.LOADING -> {}
                LspInstallationAction.INSTALL -> DownloadButton()
                LspInstallationAction.UPDATE -> {
                    UpdateButton()
                    UninstallButton()
                }
                LspInstallationAction.UNINSTALL -> UninstallButton()
            }
        }

        PreferenceGroupHeading(heading = stringResource(strings.instances))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val visibleInstances =
                server.instances.filter {
                    it.status != LspConnectionStatus.NOT_RUNNING ||
                        DefinitionPrevention.isServerPrevented(it.lspProject, it.server)
                }
            if (visibleInstances.isNotEmpty()) {
                visibleInstances.forEach { instance -> LspInstanceCard(instance, navController) }
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
            LspFeatureToggle(
                label = stringResource(strings.hover_information),
                description = stringResource(strings.hover_information_desc),
                preferenceId = "lsp_${server.id}_hover",
                server = server,
            )

            LspFeatureToggle(
                label = stringResource(strings.signature_help),
                description = stringResource(strings.signature_help_desc),
                preferenceId = "lsp_${server.id}_signature_help",
                server = server,
            )

            LspFeatureToggle(
                label = stringResource(strings.inlay_hints),
                description = stringResource(strings.inlay_hints_desc),
                preferenceId = "lsp_${server.id}_inlay_hints",
                server = server,
            )

            LspFeatureToggle(
                label = stringResource(strings.code_completion),
                description = stringResource(strings.code_completion_desc),
                preferenceId = "lsp_${server.id}_completion",
                server = server,
            )

            LspFeatureToggle(
                label = stringResource(strings.diagnostics),
                description = stringResource(strings.diagnostics_desc),
                preferenceId = "lsp_${server.id}_diagnostics",
                server = server,
            )

            LspFeatureToggle(
                label = stringResource(strings.formatting),
                description = stringResource(strings.formatting_desc),
                preferenceId = "lsp_${server.id}_formatting",
                server = server,
            )
        }
    }
}

private var snackbarJob: Job? = null

private fun showRestartRequirement(scope: CoroutineScope, server: LspServer) {
    if (snackbarJob?.isActive == true) return

    snackbarJob =
        scope.launch {
            val snackbarHost = snackbarHostStateRef.get() ?: return@launch
            val result =
                snackbarHost.showSnackbar(
                    message = strings.lsp_restart_required.getString(),
                    actionLabel = strings.restart.getString(),
                    duration = SnackbarDuration.Indefinite,
                )
            if (result == SnackbarResult.ActionPerformed) {
                server.restartAllInstances()
            }
        }
}
