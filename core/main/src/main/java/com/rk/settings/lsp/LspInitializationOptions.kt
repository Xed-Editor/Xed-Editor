package com.rk.settings.lsp

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.rk.components.ResetButton
import com.rk.editor.Editor
import com.rk.file.BuiltinFileType
import com.rk.lsp.LspServer
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.tabs.editor.EditorErrorNotice
import com.rk.theme.GitColorScheme
import com.rk.utils.isSystemInDarkTheme
import io.github.rosemoe.sora.event.ContentChangeEvent
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LspInitializationOptions(server: LspServer) {
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

    val preferenceKey = "lsp_${server.id}_initialization_options"
    val defaultInitOptions = server.getInitializationOptions(null)
    val defaultInitJson =
        defaultInitOptions?.let {
            runCatching { Gson().toJson(it) }.getOrNull()
        } ?: "{}"

    var isJsonInvalid by remember { mutableStateOf(false) }

    fun Editor.validateJson(text: String) {
        runCatching { JsonParser.parseString(text) }
            .also {
                isJsonInvalid = it.isFailure
            }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text(stringResource(strings.initialization_options)) },
                    actions = {
                        ResetButton {
                            Preference.removeKey(preferenceKey)
                            editorRef.get()?.setText(defaultInitJson)
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    ) { paddingValues ->
        val selectionColors = LocalTextSelectionColors.current
        val isDarkMode = isSystemInDarkTheme(context)
        val colorScheme = MaterialTheme.colorScheme
        val gitColorScheme = GitColorScheme.create()

        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AnimatedVisibility(visible = isJsonInvalid) {
                EditorErrorNotice(text = stringResource(strings.invalid_initialization_options))
            }

            AndroidView(
                modifier = Modifier.fillMaxSize().imePadding(),
                factory = { context ->
                    Editor(context).apply {
                        editorRef = WeakReference(this)

                        setTextSize(14f)
                        setText(Preference.getString(preferenceKey, defaultInitJson))
                        isWordwrap = false

                        validateJson(text.toString())

                        subscribeAlways(ContentChangeEvent::class.java) { event ->
                            val text = event.editor.text.toString()
                            Preference.setString(preferenceKey, text)

                            validateJson(text)
                        }

                        setThemeColors(
                            isDarkMode = isDarkMode,
                            selectionColors = selectionColors,
                            colorScheme = colorScheme,
                            gitColorScheme = gitColorScheme,
                        )

                        scope.launch { configureLanguage(BuiltinFileType.JSON.textmateScope!!) }
                    }
                },
            )
        }
    }
}
