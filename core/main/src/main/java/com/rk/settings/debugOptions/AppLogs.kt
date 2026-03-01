package com.rk.settings.debugOptions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import com.rk.utils.application
import com.rk.xededitor.BuildConfig

@Composable
fun AppLogs() {
    var logLevel by remember { mutableStateOf(LogLevel.INFO) }
    val logText = buildLogs(logLevel)

    LogScreen(logText, "App Logs Report", "app_logs", logLevel, { logLevel = it })
}

private fun buildLogs(logLevel: LogLevel): String {
    val entries =
        LogCollector.logs
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

        append(entries)
    }
}
