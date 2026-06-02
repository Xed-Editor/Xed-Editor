package com.rk.settings.lsp

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.rk.components.StyledTextField
import com.rk.lsp.LspConnectionStatus
import com.rk.lsp.LspRegistry
import com.rk.lsp.LspServer
import com.rk.lsp.LspServerInstance
import com.rk.lsp.MessageSource
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.debugOptions.LogScreen
import com.rk.utils.application
import com.rk.xededitor.BuildConfig
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.MessageType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LspServerLogs(server: LspServer, id: String) {
    val scope = rememberCoroutineScope()
    var messageType by remember { mutableStateOf(MessageType.Info) }

    val messageSources = remember { mutableStateSetOf(MessageSource.LSP, MessageSource.Runtime, MessageSource.Client) }
    var sourceDropdownExpanded by remember { mutableStateOf(false) }

    val instance = server.instances.find { it.id == id }
    val logText =
        instance?.let { buildLogs(server, it, messageType, messageSources) } ?: strings.instance_not_found.getString()

    LogScreen(logText, "Language Server Error Report", "server_logs") {
        var dropdownMenuExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = dropdownMenuExpanded,
            onExpandedChange = { dropdownMenuExpanded = !dropdownMenuExpanded },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        ) {
            StyledTextField(
                value = messageType.name + " (LSP)",
                onValueChange = {},
                shape = RoundedCornerShape(8.dp),
                maxLines = 1,
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownMenuExpanded) },
                modifier =
                    Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth().height(42.dp),
            )

            ExposedDropdownMenu(expanded = dropdownMenuExpanded, onDismissRequest = { dropdownMenuExpanded = false }) {
                MessageType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(text = type.name) },
                        onClick = {
                            messageType = type
                            dropdownMenuExpanded = false
                        },
                    )
                }
            }
        }

        IconButton(onClick = { sourceDropdownExpanded = !sourceDropdownExpanded }) {
            Icon(
                painter = painterResource(drawables.filter),
                contentDescription = stringResource(strings.filter_options),
            )

            DropdownMenu(expanded = sourceDropdownExpanded, onDismissRequest = { sourceDropdownExpanded = false }) {
                MessageSource.entries.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(text = source.name) },
                        leadingIcon = { Checkbox(checked = messageSources.contains(source), onCheckedChange = null) },
                        onClick = {
                            if (messageSources.contains(source)) {
                                messageSources.remove(source)
                            } else {
                                messageSources.add(source)
                            }
                        },
                    )
                }
            }
        }

        val isRunning =
            instance?.status != LspConnectionStatus.NOT_RUNNING &&
                instance?.status != LspConnectionStatus.CRASHED &&
                instance?.status != LspConnectionStatus.TIMEOUT
        if (isRunning) {
            IconButton(enabled = instance != null, onClick = { scope.launch { instance?.restart() } }) {
                Icon(painter = painterResource(drawables.restart), contentDescription = stringResource(strings.restart))
            }
        } else {
            IconButton(onClick = { scope.launch { instance.start() } }) {
                Icon(painter = painterResource(drawables.run), contentDescription = stringResource(strings.run))
            }
        }

        IconButton(enabled = instance != null && isRunning, onClick = { scope.launch { instance?.stop() } }) {
            Icon(painter = painterResource(drawables.stop), contentDescription = stringResource(strings.stop))
        }
    }
}

private fun buildLogs(
    server: LspServer,
    instance: LspServerInstance,
    messageType: MessageType,
    messageSources: Set<MessageSource>,
): String {
    val entries =
        instance
            .getLspLogs()
            .filter { messageSources.contains(it.source) }
            .filter { it.type == null || it.type.value <= messageType.value }
            .joinToString("\n") {
                val sourceString = it.source.name.uppercase().let { source -> "[$source]" }
                val levelString = it.type?.name?.uppercase() ?: ""
                "$sourceString $levelString ${it.message}"
            }

    val packageInfo = with(application!!) { packageManager.getPackageInfo(packageName, 0) }
    val versionName = packageInfo.versionName
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

    return buildString {
        append("[REPORT] App version: ").append(versionName).appendLine()
        append("[REPORT] Version code: ").append(versionCode).appendLine()
        append("[REPORT] Commit hash: ").append(BuildConfig.GIT_COMMIT_HASH.substring(0, 8)).appendLine()

        appendLine()

        append("[REPORT] Server name: ").append(server.serverName).appendLine()
        append("[REPORT] Server id: ").append(server.id).appendLine()
        append("[REPORT] Server status: ").append(instance.status.name).appendLine()
        append("[REPORT] Supported extensions: ").append(server.supportedExtensions).appendLine()
        append("[REPORT] Is built-in: ").append(LspRegistry.builtInServer.contains(server)).appendLine()

        appendLine()

        append(entries)
    }
}
