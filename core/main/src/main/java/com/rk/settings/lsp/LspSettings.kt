package com.rk.settings.lsp

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavController
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.InfoBlock
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.lsp.LspPersistence
import com.rk.lsp.LspRegistry
import com.rk.lsp.LspServer
import com.rk.lsp.getDominantStatusColor
import com.rk.lsp.servers.ExternalProcessServer
import com.rk.lsp.servers.ExternalSocketServer
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.utils.parseExtensions
import com.rk.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LspSettings(navController: NavController) {
    var showDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
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
    fun BuiltinServersSection() {
        PreferenceGroup(heading = stringResource(strings.built_in)) {
            LspRegistry.builtInServer.forEach { server ->
                key(server.id) { LspServerItem(context, scope, server, navController, refreshKey) }
            }
        }
    }

    @Composable
    fun ExtensionServersSection(extensionServers: List<LspServer>) {
        PreferenceGroup(heading = stringResource(strings.ext)) {
            extensionServers.forEach { server ->
                key(server.id) { LspServerItem(context, scope, server, navController, refreshKey) }
            }
        }
    }

    @Composable
    fun ExternalServersSection() {
        PreferenceGroup(heading = stringResource(strings.external)) {
            LspRegistry.externalServers.forEachIndexed { index, server ->
                key(server.id) {
                    val icon = server.icon ?: drawables.unknown_document
                    SettingsToggle(
                        label = server.languageName,
                        default = true,
                        description = server.serverName,
                        singleLineDescription = true,
                        showSwitch = false,
                        onClick = { navController.navigate("${SettingsRoutes.LspServerDetail.route}/${server.id}") },
                        startWidget = { LspServerIcon(server, icon) },
                        endWidget = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (server !is ExternalSocketServer && server !is ExternalProcessServer) {
                                            return@IconButton
                                        }

                                        editingIndex = index
                                        showDialog = true
                                    }
                                ) {
                                    Icon(imageVector = Icons.Outlined.Edit, contentDescription = null)
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch { server.disconnectAllInstances() }
                                        LspRegistry.removeExternalServer(server)
                                        LspPersistence.saveServers()
                                    }
                                ) {
                                    Icon(imageVector = Icons.Outlined.Delete, stringResource(strings.delete))
                                }
                            }
                        },
                    )
                }
            }
        }
    }

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

        if (LspRegistry.builtInServer.isNotEmpty()) BuiltinServersSection()

        val extensionServers = LspRegistry.extensionServers
        if (extensionServers.isNotEmpty()) ExtensionServersSection(extensionServers)

        if (LspRegistry.externalServers.isNotEmpty()) ExternalServersSection()

        Spacer(modifier = Modifier.height(60.dp))

        if (showDialog) {
            ExternalLSPDialog(
                onDismiss = {
                    showDialog = false
                    editingIndex = null
                },
                onConfirm = { newServer, replaceIndex ->
                    if (replaceIndex == -1) {
                        LspRegistry.addExternalServer(newServer)
                        scope.launch { newServer.connectAllSuitableEditors() }
                    } else {
                        val oldServer = LspRegistry.externalServers[replaceIndex]
                        scope.launch { oldServer.disconnectAllInstances() }

                        LspRegistry.replaceExternalServer(replaceIndex, newServer)
                        scope.launch { newServer.connectAllSuitableEditors() }
                    }
                    LspPersistence.saveServers()
                },
                editingIndex = editingIndex,
            )
        }
    }
}

@Composable
private fun LspServerItem(
    context: Context,
    scope: CoroutineScope,
    server: LspServer,
    navController: NavController,
    refreshKey: Int,
) {
    val icon = server.icon ?: drawables.unknown_document
    SettingsToggle(
        label = server.languageName,
        default = Preference.getBoolean("lsp_${server.id}", true),
        description = server.serverName,
        singleLineDescription = true,
        showSwitch = true,
        onClick = { navController.navigate("${SettingsRoutes.LspServerDetail.route}/${server.id}") },
        startWidget = { LspServerIcon(server, icon) },
        sideEffect = {
            if (it) {
                scope.launch { server.connectAllSuitableEditors() }
            } else {
                scope.launch { server.disconnectAllInstances() }
            }
            Preference.setBoolean("lsp_${server.id}", it)
        },
        endWidget = {
            val status by rememberLspInstallStatus(context, server, refreshKey)

            when (status) {
                LspInstallationAction.INSTALL -> {
                    IconButton(onClick = { server.install(context) }) {
                        Icon(
                            painter = painterResource(drawables.download),
                            contentDescription = stringResource(strings.download),
                        )
                    }
                }

                LspInstallationAction.UPDATE -> {
                    IconButton(onClick = { server.update(context) }) {
                        Icon(
                            painter = painterResource(drawables.update),
                            contentDescription = stringResource(strings.update),
                        )
                    }
                }

                else -> {}
            }
        },
    )
}

@Composable
fun rememberLspInstallStatus(context: Context, server: LspServer, refreshKey: Int): State<LspInstallationAction> {
    // Keep the previous status for refresh key changes to avoid flashing
    var previousStatus by remember { mutableStateOf<LspInstallationAction?>(null) }

    return key(refreshKey) {
        produceState(previousStatus ?: LspInstallationAction.LOADING) {
            withContext(Dispatchers.IO) {
                if (server.isInstalled(context)) {
                    value = LspInstallationAction.UNINSTALL
                    if (server.isUpdatable(context)) {
                        value = LspInstallationAction.UPDATE
                    }
                } else {
                    value = LspInstallationAction.INSTALL
                }
            }

            previousStatus = value
        }
    }
}

@Composable
private fun LspServerIcon(server: LspServer, i: Int) {
    BadgedBox(badge = { server.getDominantStatusColor()?.let { color -> Badge(containerColor = color) } }) {
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
        extensionsError = validateExtensions(newValue)
    }

    fun validateExtensions(value: String): String? {
        val parsedExtensions = parseExtensions(value)

        if (parsedExtensions.isEmpty()) {
            return strings.unsupported_file_ext.getString()
        }

        val invalid = parsedExtensions.filter { it.contains(" ") }
        if (invalid.isNotEmpty()) {
            return "${strings.unsupported_file_ext.getString()}: ${invalid.joinToString(", ") { ".$it" }}"
        }

        return null
    }
}

private enum class LspType(val label: String) {
    SOCKET(strings.socket.getString()),
    PROCESS(strings.process.getString()),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalLSPDialog(onDismiss: () -> Unit, onConfirm: (LspServer, Int) -> Unit, editingIndex: Int?) {
    var selected by remember { mutableStateOf(LspType.SOCKET) }

    val editingServer = remember(editingIndex) { editingIndex?.let { LspRegistry.externalServers[it] } }
    val dialogState = remember { ExternalLspDialogState() }

    LaunchedEffect(editingServer) {
        editingServer?.let { server ->
            when (server) {
                is ExternalSocketServer -> {
                    selected = LspType.SOCKET
                    dialogState.lspHost = server.host
                    dialogState.lspPort = server.port.toString()
                    dialogState.onExtensionsChange(server.supportedExtensions.joinToString(", ") { ".$it" })
                }
                is ExternalProcessServer -> {
                    selected = LspType.PROCESS
                    dialogState.lspCommand = server.command
                    dialogState.onExtensionsChange(server.supportedExtensions.joinToString(", ") { ".$it" })
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.external_lsp)) },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                            var server: LspServer? = null
                            server =
                                when (selected) {
                                    LspType.SOCKET ->
                                        ExternalSocketServer(
                                            host = dialogState.lspHost,
                                            port = dialogState.lspPort.toInt(),
                                            supportedExtensions = parseExtensions(dialogState.lspExtensions),
                                        )

                                    LspType.PROCESS ->
                                        ExternalProcessServer(
                                            command = dialogState.lspCommand,
                                            supportedExtensions = parseExtensions(dialogState.lspExtensions),
                                        )
                                }
                            onConfirm(server, editingIndex ?: -1)
                        }
                        .onFailure { toast(it.message) }

                    onDismiss()
                },
                enabled =
                    when (selected) {
                        LspType.SOCKET -> dialogState.socketConfirmEnabled
                        LspType.PROCESS -> dialogState.externalConfirmEnabled
                    },
            ) {
                if (editingServer == null) {
                    Text(stringResource(strings.add))
                } else {
                    Text(stringResource(strings.save))
                }
            }
        },
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(strings.cancel)) } },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    LspType.entries.forEach { type ->
                        SegmentedButton(
                            selected = selected == type,
                            onClick = { selected = type },
                            label = { Text(type.label) },
                            shape =
                                SegmentedButtonDefaults.itemShape(index = type.ordinal, count = LspType.entries.size),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                when (selected) {
                    LspType.SOCKET -> SocketServerSection(dialogState)
                    LspType.PROCESS -> ProcessServerSection(dialogState)
                }
            }
        },
    )
}
