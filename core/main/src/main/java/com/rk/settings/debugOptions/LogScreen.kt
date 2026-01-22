package com.rk.settings.debugOptions

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.rk.activities.settings.SettingsActivity
import com.rk.crashhandler.CrashHandler.logErrorOrExit
import com.rk.editor.Editor
import com.rk.file.FileType
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.theme.XedTheme
import com.rk.utils.copyToClipboard
import com.rk.utils.dialog
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(logText: String, issueTitle: String, copyLabel: String) {
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

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
                        title = { Text(stringResource(strings.logs)) },
                        actions = {
                            TextButton(onClick = { copyToClipboard(copyLabel, logText, true) }) {
                                Text(stringResource(strings.copy))
                            }

                            TextButton(
                                onClick = {
                                    runCatching { reportLogs(logText, issueTitle, copyLabel) }
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
            val selectionColors = LocalTextSelectionColors.current
            val isDarkMode = isSystemInDarkTheme()
            val colorScheme = MaterialTheme.colorScheme

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
                            selectionColors = selectionColors,
                            colorScheme = colorScheme,
                        )

                        scope.launch { setLanguage(FileType.LOG.textmateScope!!) }
                    }
                },
                update = { editor -> editor.setText(logText) },
            )
        }
    }
}

private fun reportLogs(logText: String, issueTitle: String, copyLabel: String) {
    val context = SettingsActivity.instance!!

    val encodedTitle = URLEncoder.encode(issueTitle, StandardCharsets.UTF_8.toString())
    val urlStart = "https://github.com/Xed-Editor/Xed-Editor/issues/new?title=$encodedTitle&body="
    val url = urlStart + URLEncoder.encode("```log \n${logText}\n ```", StandardCharsets.UTF_8.toString())
    if (url.length > 2048) {
        val trimmedUrl =
            urlStart + URLEncoder.encode("```log \nPaste the logs here\n ```", StandardCharsets.UTF_8.toString())
        dialog(
            context = context,
            title = strings.logs_too_long.getString(),
            msg = strings.logs_too_long_desc.getString(),
            okString = strings.continue_action,
            onOk = {
                copyToClipboard(copyLabel, logText, true)
                val browserIntent = Intent(Intent.ACTION_VIEW, trimmedUrl.toUri())
                context.startActivity(browserIntent)
            },
            cancelable = false,
        )
        return
    }
    val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
    context.startActivity(browserIntent)
}
