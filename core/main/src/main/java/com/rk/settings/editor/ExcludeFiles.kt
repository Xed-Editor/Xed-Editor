package com.rk.settings.editor

import android.content.Intent
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.rk.components.ResetButton
import com.rk.editor.Editor
import com.rk.file.BuiltinFileType
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.ReactiveSettings
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorNotice
import com.rk.theme.XedTheme
import com.rk.utils.isSystemInDarkTheme
import io.github.rosemoe.sora.event.ContentChangeEvent
import java.lang.ref.WeakReference
import kotlinx.coroutines.launch

val DEFAULT_EXCLUDED_FILES_DRAWER = listOf("**/.git", "**/.svn", "**/.hg", "**/.DS_Store", "**/Thumbs.db")

val DEFAULT_EXCLUDED_FILES_SEARCH =
    listOf(
        "**/node_modules/**",
        "**/bower_components/**",
        "**/jspm_packages/**",
        "**/.npm/**",
        "**/flow-typed/**",
        "**/vendor/**",
        "**/composer/**",
        "**/venv/**",
        "**/.virtualenv/**",
        "**/__pycache__/**",
        "**/.pytest_cache/**",
        "**/.eggs/**",
        "**/*.egg-info/**",
        "**/.git/**",
        "**/.svn/**",
        "**/.hg/**",
        "**/.vscode/**",
        "**/.idea/**",
        "**/.vs/**",
        "**/.project/**",
        "**/.settings/**",
        "**/.classpath/**",
        "**/dist/**",
        "**/build/**",
        "**/out/**",
        "**/target/**",
        "**/bin/**",
        "**/obj/**",
        "**/coverage/**",
        "**/.nyc_output/**",
        "**/htmlcov/**",
        "**/temp/**",
        "**/tmp/**",
        "**/.cache/**",
        "**/logs/**",
        "**/.sass-cache/**",
        "**/.DS_Store/**",
        "**/Thumbs.db/**",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcludeFiles(isDrawer: Boolean) {
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    var editorRef = remember { WeakReference<Editor?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            editorRef.get()?.release()
            editorRef = WeakReference(null)
        }
    }

    // TODO: Maybe not use XedTheme and Scaffold but PreferenceScaffold/PreferenceLayout
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
                        title = {
                            Text(
                                stringResource(
                                    if (isDrawer) strings.exclude_files_drawer else strings.exclude_files_search
                                )
                            )
                        },
                        actions = { ResetButton { resetFiles(editorRef.get(), isDrawer) } },
                    )
                    HorizontalDivider()
                }
            }
        ) { paddingValues ->
            val selectionColors = LocalTextSelectionColors.current
            val isDarkMode = isSystemInDarkTheme(context)
            val colorScheme = MaterialTheme.colorScheme

            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                EditorNotice(
                    text = stringResource(strings.glob_docs),
                    actionButton = {
                        IconButton(
                            onClick = {
                                val url = "https://code.visualstudio.com/docs/editor/glob-patterns"
                                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(
                                painter = painterResource(drawables.open_in_new),
                                contentDescription = stringResource(strings.open),
                            )
                        }
                    },
                )

                AndroidView(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    factory = { context ->
                        Editor(context).apply {
                            editorRef = WeakReference(this)

                            setTextSize(10f)
                            if (isDrawer) {
                                setText(Settings.excluded_files_drawer)
                            } else {
                                setText(Settings.excluded_files_search)
                            }
                            isWordwrap = false

                            subscribeAlways(ContentChangeEvent::class.java) {
                                if (isDrawer) {
                                    Settings.excluded_files_drawer = it.editor.text.toString()
                                } else {
                                    Settings.excluded_files_search = it.editor.text.toString()
                                }
                                ReactiveSettings.update()
                            }

                            setThemeColors(
                                isDarkMode = isDarkMode,
                                selectionColors = selectionColors,
                                colorScheme = colorScheme,
                            )

                            scope.launch { setLanguage(BuiltinFileType.IGNORE.textmateScope!!) }
                        }
                    },
                )
            }
        }
    }
}

/** Reset order of commands and symbols to default */
private fun resetFiles(editor: Editor?, isDrawer: Boolean) {
    if (isDrawer) {
        Preference.removeKey("excluded_files_drawer")
        editor?.setText(DEFAULT_EXCLUDED_FILES_DRAWER.joinToString("\n"))
    } else {
        Preference.removeKey("excluded_files_search")
        editor?.setText(DEFAULT_EXCLUDED_FILES_SEARCH.joinToString("\n"))
    }
}
