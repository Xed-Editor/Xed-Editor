package com.rk.settings.debugOptions

import androidx.compose.runtime.Composable
import androidx.core.content.pm.PackageInfoCompat
import com.rk.utils.application
import com.rk.xededitor.BuildConfig

@Composable
fun AppLogs() {
    val logText = buildLogs()
    LogScreen(logText, "App Logs Report", "app_logs")
}

private fun buildLogs(): String {
    val entries = LogCollector.logs.joinToString("\n") { "[${it.level.name.uppercase()}] ${it.message}" }
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
