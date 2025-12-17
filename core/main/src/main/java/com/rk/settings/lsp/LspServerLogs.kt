package com.rk.settings.lsp

import android.content.Intent
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.rk.activities.settings.SettingsActivity
import com.rk.crashhandler.CrashHandler.logErrorOrExit
import com.rk.editor.Editor
import com.rk.file.FileType
import com.rk.lsp.BaseLspServer
import com.rk.lsp.builtInServer
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.theme.XedTheme
import com.rk.utils.application
import com.rk.utils.copyToClipboard
import com.rk.utils.dialog
import com.rk.xededitor.BuildConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LspServerLogs(server: BaseLspServer) {
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val context = SettingsActivity.instance!!
    val logText = buildLogs(server)

    XedTheme {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        title = { Text("Server logs") },
                        actions = {
                            TextButton(onClick = { copyToClipboard("server_logs", logText, true) }) {
                                Text(stringResource(strings.copy))
                            }

                            TextButton(
                                onClick = {
                                    runCatching {
                                            val urlStart =
                                                "https://github.com/Xed-Editor/Xed-Editor/issues/new?title=Language%20Server%20Error%20Report&body="
                                            val url =
                                                urlStart +
                                                    URLEncoder.encode(
                                                        "```log \n${logText}\n ```",
                                                        StandardCharsets.UTF_8.toString(),
                                                    )
                                            if (url.length > 2048) {
                                                val trimmedUrl =
                                                    urlStart +
                                                        URLEncoder.encode(
                                                            "```log \nPaste the logs here\n ```",
                                                            StandardCharsets.UTF_8.toString(),
                                                        )
                                                dialog(
                                                    context = SettingsActivity.instance!!,
                                                    title = strings.logs_too_long.getString(),
                                                    msg = strings.logs_too_long_desc.getString(),
                                                    okString = strings.continue_action,
                                                    onOk = {
                                                        copyToClipboard("server_logs", logText, true)
                                                        val browserIntent =
                                                            Intent(Intent.ACTION_VIEW, trimmedUrl.toUri())
                                                        context.startActivity(browserIntent)
                                                    },
                                                    cancelable = false,
                                                )
                                                return@runCatching
                                            }
                                            val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                                            context.startActivity(browserIntent)
                                        }
                                        .onFailure { logErrorOrExit(it) }
                                }
                            ) {
                                Text(stringResource(strings.report_issue))
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        ) { paddingValues ->
            val surfaceColor =
                if (isSystemInDarkTheme()) {
                    MaterialTheme.colorScheme.surfaceDim
                } else {
                    MaterialTheme.colorScheme.surface
                }
            val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
            val highSurfaceContainer = MaterialTheme.colorScheme.surfaceContainerHigh
            val selectionColors = LocalTextSelectionColors.current
            val realSurface = MaterialTheme.colorScheme.surface
            val selectionBackground = selectionColors.backgroundColor
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            val colorPrimary = MaterialTheme.colorScheme.primary
            val colorPrimaryContainer = MaterialTheme.colorScheme.primaryContainer
            val colorSecondary = MaterialTheme.colorScheme.secondary
            val handleColor = selectionColors.handleColor
            val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

            val gutterColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            val currentLineColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.8f)

            val divider = MaterialTheme.colorScheme.outlineVariant
            val isDarkMode = isSystemInDarkTheme()

            AndroidView(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                factory = { context ->
                    Editor(context).apply {
                        setTextSize(10f)
                        setText(logText)
                        editable = false
                        isWordwrap = false
                        setThemeColors(
                            isDarkMode = isDarkMode,
                            editorSurface = surfaceColor.toArgb(),
                            surfaceContainer = surfaceContainer.toArgb(),
                            highSurfaceContainer = highSurfaceContainer.toArgb(),
                            surface = realSurface.toArgb(),
                            onSurface = onSurfaceColor.toArgb(),
                            colorPrimary = colorPrimary.toArgb(),
                            colorPrimaryContainer = colorPrimaryContainer.toArgb(),
                            colorSecondary = colorSecondary.toArgb(),
                            secondaryContainer = secondaryContainer.toArgb(),
                            selectionBg = selectionBackground.toArgb(),
                            handleColor = handleColor.toArgb(),
                            gutterColor = gutterColor.toArgb(),
                            currentLine = currentLineColor.toArgb(),
                            dividerColor = divider.toArgb(),
                        )

                        scope.launch { setLanguage(FileType.LOG.textmateScope!!) }
                    }
                },
                update = { editor -> editor.setText(logText) },
            )
        }
    }
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
        append("[DEBUG] Is built-in: ").append(builtInServer.contains(server)).appendLine()

        appendLine()

        append(entries)
    }
}
