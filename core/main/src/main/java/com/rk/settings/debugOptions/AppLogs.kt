package com.rk.settings.debugOptions

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.rk.components.XedDropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.rk.components.StyledTextField
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.application
import com.rk.xededitor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext

@Composable
fun AppLogs() {
    var logLevel by remember { mutableStateOf(LogLevel.INFO) }
    var logText by remember { mutableStateOf(strings.loading.getString()) }

    LaunchedEffect(logLevel) {
        withContext(Dispatchers.IO) {
            logText = buildLogs(logLevel)
        }
    }

    val logFlow =
        remember(logLevel) {
            if (Settings.enable_logcat) {
                LogcatService.logFlow.filter { it.matchesLogLevel(logLevel) }
            } else {
                null
            }
        }

    LogScreen(logText, "App Logs Report", "app_logs", flow = logFlow) {
        LogLevelDropdown(logLevel, { logLevel = it })
    }
}

private fun buildLogs(logLevel: LogLevel): String {
    val entries =
        if (Settings.enable_logcat) {
            val logsCopy =
                synchronized(LogcatService.logcatLogs) {
                    LogcatService.logcatLogs.toList()
                }
            logsCopy
                .filter { line ->
                    line.matchesLogLevel(logLevel)
                }
                .takeLast(1000)
                .joinToString("\n")
        } else {
            LogCollector.logs
                .filter { it.level.ordinal <= logLevel.ordinal }
                .joinToString("\n") { "[${it.level.name.uppercase()}] ${it.message}" }
        }

    val packageInfo = with(application!!) { packageManager.getPackageInfo(packageName, 0) }
    val versionName = packageInfo.versionName
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

    return buildString {
        append("[REPORT] App version: ").append(versionName).appendLine()
        append("[REPORT] Version code: ").append(versionCode).appendLine()
        append("[REPORT] Commit hash: ").append(BuildConfig.GIT_COMMIT_HASH.substring(0, 8)).appendLine()

        appendLine()

        append(entries)
    }
}

private fun String.matchesLogLevel(level: LogLevel): Boolean {
    if (isEmpty()) return true

    val idx = indexOf('/')
    if (idx != 1) return true

    val priority =
        when (this[0]) {
            'V',
            'D' -> 5
            'I' -> 3
            'W' -> 2
            'E',
            'F' -> 1
            else -> 5
        }

    return priority <= level.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowScope.LogLevelDropdown(logLevel: LogLevel, onLogLevelChange: (LogLevel) -> Unit) {
    var dropdownMenuExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = dropdownMenuExpanded,
        onExpandedChange = { dropdownMenuExpanded = !dropdownMenuExpanded },
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
    ) {
        StyledTextField(
            value = logLevel.label,
            onValueChange = {},
            shape = RoundedCornerShape(8.dp),
            maxLines = 1,
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownMenuExpanded) },
            modifier =
                Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth().height(42.dp),
        )

        ExposedDropdownMenu(expanded = dropdownMenuExpanded, onDismissRequest = { dropdownMenuExpanded = false }) {
            LogLevel.entries.forEach { level ->
                XedDropdownMenuItem(
                    text = { Text(text = level.label) },
                    onClick = {
                        onLogLevelChange(level)
                        dropdownMenuExpanded = false
                    },
                )
            }
        }
    }
}
