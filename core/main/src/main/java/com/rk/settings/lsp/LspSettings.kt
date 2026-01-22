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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.InfoBlock
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspPersistence
import com.rk.lsp.LspRegistry
import com.rk.lsp.getConnectionColor
import com.rk.lsp.servers.ExternalProcessServer
import com.rk.lsp.servers.ExternalSocketServer
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.utils.parseExtensions
import com.rk.utils.toast

@Composable
fun LspSettings(modifier: Modifier = Modifier, navController: NavController) {
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

        if (LspRegistry.builtInServer.isNotEmpty()) {
            PreferenceGroup(heading = stringResource(strings.built_in)) {
                LspRegistry.builtInServer.forEach { server ->
                    SettingsToggle(
                        label = server.languageName,
                        default = Preference.getBoolean("lsp_${server.id}", false),
                        description = server.serverName,
                        showSwitch = true,
                        onClick = { navController.navigate("${SettingsRoutes.LspServerDetail.route}/${server.id}") },
                        startWidget = server.icon?.let { { LanguageServerIcon(server, it) } },
                        sideEffect = { Preference.setBoolean("lsp_${server.id}", it) },
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(strings.no_language_server))
            }
        }

        val extensionServers = LspRegistry.extensionServers
        if (extensionServers.isNotEmpty()) {
            PreferenceGroup(heading = stringResource(strings.ext)) {
                extensionServers.forEach { server ->
                    SettingsToggle(
                        label = server.languageName,
                        default = Preference.getBoolean("lsp_${server.id}", false),
                        description = server.serverName,
                        showSwitch = true,
                        onClick = { navController.navigate("${SettingsRoutes.LspServerDetail.route}/${server.id}") },
                        startWidget = server.icon?.let { { LanguageServerIcon(server, it) } },
                        sideEffect = { Preference.setBoolean("lsp_${server.id}", it) },
                    )
                }
            }
        }

        if (LspRegistry.externalServers.isNotEmpty()) {
            PreferenceGroup(heading = stringResource(strings.external)) {
                LspRegistry.externalServers.forEach { server ->
                    SettingsToggle(
                        label = server.languageName,
                        default = true,
                        description = server.serverName,
                        showSwitch = false,
                        onClick = { navController.navigate("${SettingsRoutes.LspServerDetail.route}/${server.id}") },
                        startWidget = server.icon?.let { { LanguageServerIcon(server, it) } },
                        endWidget = {
                            IconButton(
                                onClick = {
                                    LspRegistry.externalServers.remove(server)
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
                    LspRegistry.externalServers.add(server)
                    LspPersistence.saveServers()
                },
            )
        }
    }
}

@Composable
private fun LanguageServerIcon(server: BaseLspServer, i: Int) {
    BadgedBox(badge = { server.getConnectionColor()?.let { color -> Badge(containerColor = color) } }) {
        Icon(modifier = Modifier.padding(start = 16.dp), painter = painterResource(i), contentDescription = null)
    }
}

class ExternalLspDialogState {
    // Shared
    var lspExtensions by mutableStateOf("")
    var extensionsError by mutableStateOf<String?>(null)

    // Command
    var lspCommand by mutableStateOf("")
    var externalError by mutableStateOf<String?>(null)
    val externalConfirmEnabled by derivedStateOf {
        externalError == null && extensionsError == null && lspCommand.isNotBlank() && lspExtensions.isNotBlank()
    }

    // Socket
    var lspHost by mutableStateOf("localhost")
    var lspPort by mutableStateOf("")
    var hostError by mutableStateOf<String?>(null)
    var portError by mutableStateOf<String?>(null)
    val socketConfirmEnabled by derivedStateOf {
        hostError == null &&
            portError == null &&
            extensionsError == null &&
            lspPort.isNotBlank() &&
            lspExtensions.isNotBlank()
    }

    /**
     * Should be called when the value of the extensions input field changes. It handles the validation of the
     * extensions and makes sure that the extensions value in the external and socket page are synced.
     */
    fun onExtensionsChange(newValue: String) {
        lspExtensions = newValue
        extensionsError = null

        val parsedExtensions = parseExtensions(lspExtensions)
        if (parsedExtensions.isEmpty()) {
            extensionsError = strings.unsupported_file_ext.getString()
        } else {
            val invalid = parsedExtensions.filter { it.contains(" ") }
            if (invalid.isNotEmpty()) {
                extensionsError =
                    "${strings.unsupported_file_ext.getString()}: ${invalid.joinToString(", ") { ".$it" }}"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalLSP(onDismiss: () -> Unit, onConfirm: (BaseLspServer) -> Unit) {
    val socketLabel = stringResource(strings.socket)
    val processLabel = stringResource(strings.process)
    var selected by remember { mutableStateOf(socketLabel) }
    val options = listOf(socketLabel, processLabel)

    val dialogState = remember { ExternalLspDialogState() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.external_lsp)) },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                            var server: BaseLspServer? = null
                            when (selected) {
                                socketLabel ->
                                    server =
                                        ExternalSocketServer(
                                            host = dialogState.lspHost,
                                            port = dialogState.lspPort.toInt(),
                                            supportedExtensions = parseExtensions(dialogState.lspExtensions),
                                        )
                                processLabel ->
                                    server =
                                        ExternalProcessServer(
                                            command = dialogState.lspCommand,
                                            supportedExtensions = parseExtensions(dialogState.lspExtensions),
                                        )
                            }
                            server?.let { onConfirm(it) }
                        }
                        .onFailure { toast(it.message) }

                    onDismiss()
                },
                enabled =
                    when (selected) {
                        socketLabel -> dialogState.socketConfirmEnabled
                        processLabel -> dialogState.externalConfirmEnabled
                        else -> false
                    },
            ) {
                Text(stringResource(strings.add))
            }
        },
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(strings.cancel)) } },
        text = {
            Column {
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
                    socketLabel -> ExternalSocketServer(dialogState)
                    processLabel -> ExternalProcessServer(dialogState)
                }
            }
        },
    )
}
