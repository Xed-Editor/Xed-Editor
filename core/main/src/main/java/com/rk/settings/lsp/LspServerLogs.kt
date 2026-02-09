package com.rk.settings.lsp

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.core.content.pm.PackageInfoCompat
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspRegistry
import com.rk.settings.debugOptions.LogScreen
import com.rk.utils.application
import com.rk.xededitor.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LspServerLogs(server: BaseLspServer) {
    val logText = buildLogs(server)
    LogScreen(logText, "Language Server Error Report", "server_logs")
}

private fun buildLogs(server: BaseLspServer): String {
    val entries = server.logs.joinToString("\n") { "[${it.level.name.uppercase()}] ${it.message}" }
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
        append("[DEBUG] Server status: ").append(server.status.name).appendLine()
        append("[DEBUG] Supported extensions: ").append(server.supportedExtensions).appendLine()
        append("[DEBUG] Is built-in: ").append(LspRegistry.builtInServer.contains(server)).appendLine()

        appendLine()

        append(entries)
    }
}
