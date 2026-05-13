package com.rk.settings.terminal

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
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorNotice
import com.rk.utils.isSystemInDarkTheme
import io.github.rosemoe.sora.event.ContentChangeEvent
import java.lang.ref.WeakReference
import kotlinx.coroutines.launch

const val DEFAULT_TERMINAL_EXTRA_KEYS =
    ("[" +
        "\n  [" +
        "\n    \"ESC\"," +
        "\n    {" +
        "\n      \"key\": \"/\"," +
        "\n      \"popup\": \"\\\\\"" +
        "\n    }," +
        "\n    {" +
        "\n      \"key\": \"-\"," +
        "\n      \"popup\": \"|\"" +
        "\n    }," +
        "\n    \"HOME\"," +
        "\n    \"UP\"," +
        "\n    \"END\"," +
        "\n    \"PGUP\"" +
        "\n  ]," +
        "\n  [" +
        "\n    \"TAB\"," +
        "\n    \"CTRL\"," +
        "\n    \"ALT\"," +
        "\n    \"LEFT\"," +
        "\n    \"DOWN\"," +
        "\n    \"RIGHT\"," +
        "\n    \"PGDN\"" +
        "\n  ]" +
        "\n]")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalExtraKeys() {
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

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text(stringResource(strings.change_extra_keys)) },
                    actions = { ResetButton { resetFiles(editorRef.get()) } },
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
                text = stringResource(strings.see_termux_extra_keys),
                actionButton = {
                    IconButton(
                        onClick = {
                            val url = "https://wiki.termux.com/wiki/Touch_Keyboard#Extra_Keys_Row"
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
                        setText(Settings.terminal_extra_keys)
                        isWordwrap = false

                        subscribeAlways(ContentChangeEvent::class.java) {
                            Settings.terminal_extra_keys = it.editor.text.toString()
                        }

                        setThemeColors(
                            isDarkMode = isDarkMode,
                            selectionColors = selectionColors,
                            colorScheme = colorScheme,
                        )

                        scope.launch { setLanguage(BuiltinFileType.JSON.textmateScope!!) }
                    }
                },
            )
        }
    }
}

/** Reset order of commands and symbols to default */
private fun resetFiles(editor: Editor?) {
    Preference.removeKey("terminal_extra_keys")
    editor?.setText(DEFAULT_TERMINAL_EXTRA_KEYS)
}
