package com.rk.settings.debugOptions

import android.content.Intent
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.rk.activities.settings.SettingsActivity
import com.rk.components.StyledTextField
import com.rk.crashhandler.CrashHandler.logErrorOrExit
import com.rk.editor.Editor
import com.rk.file.BuiltinFileType
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.search.EditorSearchPanel
import com.rk.tabs.editor.CodeEditorState
import com.rk.theme.XedTheme
import com.rk.utils.copyToClipboard
import com.rk.utils.dialog
import java.lang.ref.WeakReference
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    logText: String,
    issueTitle: String,
    copyLabel: String,
    logLevel: LogLevel,
    onLogLevelChange: (LogLevel) -> Unit,
    actionButtons: @Composable (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val editorState = remember { CodeEditorState() }
    var dropdownMenuExpanded by remember { mutableStateOf(false) }

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
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Surface {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            IconButton(onClick = { editorState.isSearching = !editorState.isSearching }) {
                                Icon(
                                    painter = painterResource(drawables.search),
                                    contentDescription = stringResource(strings.search),
                                )
                            }

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
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownMenuExpanded)
                                    },
                                    modifier = Modifier.menuAnchor().fillMaxWidth().height(42.dp),
                                )

                                ExposedDropdownMenu(
                                    expanded = dropdownMenuExpanded,
                                    onDismissRequest = { dropdownMenuExpanded = false },
                                ) {
                                    LogLevel.entries.forEach { level ->
                                        DropdownMenuItem(
                                            text = { Text(text = level.label) },
                                            onClick = {
                                                onLogLevelChange(level)
                                                dropdownMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }

                            actionButtons?.invoke()
                        }

                        HorizontalDivider()
                    }
                }

                EditorSearchPanel(editorState)
                if (editorState.isSearching) {
                    HorizontalDivider()
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    factory = { context ->
                        Editor(context).apply {
                            editorState.editor = WeakReference(this)

                            setTextSize(10f)
                            setText(logText)

                            editorState.editable = false
                            editable = editorState.editable

                            isWordwrap = false
                            setThemeColors(
                                isDarkMode = isDarkMode,
                                selectionColors = selectionColors,
                                colorScheme = colorScheme,
                            )

                            scope.launch { setLanguage(BuiltinFileType.LOG.textmateScope!!) }
                        }
                    },
                    update = { editor ->
                        val x = editor.offsetX
                        val y = editor.offsetY

                        editor.setText(logText)

                        editor.scroller.forceFinished(true)
                        editor.scroller.startScroll(x, y, 0, 0, 0)
                        editor.scroller.abortAnimation()
                    },
                )
            }
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
