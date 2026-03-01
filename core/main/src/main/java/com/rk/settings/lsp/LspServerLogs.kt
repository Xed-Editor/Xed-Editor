package com.rk.settings.lsp

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.PackageInfoCompat
import com.rk.lsp.BaseLspServer
import com.rk.lsp.BaseLspServerInstance
import com.rk.lsp.LspConnectionStatus
import com.rk.lsp.LspRegistry
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.debugOptions.LogLevel
import com.rk.settings.debugOptions.LogScreen
import com.rk.utils.application
import com.rk.xededitor.BuildConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LspServerLogs(server: BaseLspServer, id: String) {
    val scope = rememberCoroutineScope()
    var logLevel by remember { mutableStateOf(LogLevel.INFO) }

    val instance = server.instances.find { it.id == id }
    val logText = instance?.let { buildLogs(server, it, logLevel) } ?: strings.instance_not_found.getString()

    LogScreen(logText, "Language Server Error Report", "server_logs", logLevel, { logLevel = it }) {
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

private fun buildLogs(server: BaseLspServer, instance: BaseLspServerInstance, logLevel: LogLevel): String {
    val entries =
        instance
            .getLogs()
            .filter { it.level.ordinal >= logLevel.ordinal }
            .joinToString("\n") { "[${it.level.name.uppercase()}] ${it.message}" }

    val packageInfo = with(application!!) { packageManager.getPackageInfo(packageName, 0) }
    val versionName = packageInfo.versionName
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

    return buildString {
        append("[DEBUG] App version: ").append(versionName).appendLine()
        append("[DEBUG] Version code: ").append(versionCode).appendLine()
        append("[DEBUG] Commit hash: ").append(BuildConfig.GIT_COMMIT_HASH.substring(0, 8)).appendLine()

        appendLine()

        append("[DEBUG] Server name: ").append(server.serverName).appendLine()
        append("[DEBUG] Server id: ").append(server.id).appendLine()
        append("[DEBUG] Server status: ").append(instance.status.name).appendLine()
        append("[DEBUG] Supported extensions: ").append(server.supportedExtensions).appendLine()
        append("[DEBUG] Is built-in: ").append(LspRegistry.builtInServer.contains(server)).appendLine()

        appendLine()

        append(entries)
    }
}
