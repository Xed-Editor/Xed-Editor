package com.rk.tabs.editor

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.fileTreeViewModel
import com.rk.activities.main.snackbarHostStateRef
import com.rk.color.ColorFormat
import com.rk.color.parseUnknownColor
import com.rk.commands.KeybindingsManager
import com.rk.editor.Editor
import com.rk.editor.LanguageManager
import com.rk.editor.intelligent.IntelligentFeature
import com.rk.file.FileObject
import com.rk.lsp.LspConnector
import com.rk.lsp.LspRegistry
import com.rk.lsp.LspServer
import com.rk.lsp.createLspTextActions
import com.rk.resources.drawables
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.utils.info
import com.rk.utils.logWarn
import com.rk.utils.toast
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.InlayHintClickEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.event.PublishDiagnosticsEvent
import io.github.rosemoe.sora.lang.styling.inlayHint.ColorInlayHint
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.TextRange
import io.github.rosemoe.sora.widget.component.TextActionItem
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(DelicateCoroutinesApi::class, ExperimentalLayoutApi::class)
@Composable
fun EditorTab.CodeEditor(
    modifier: Modifier = Modifier,
    intelligentFeatures: List<IntelligentFeature>,
    onTextChange: () -> Unit,
) {
    val selectionColors = LocalTextSelectionColors.current
    val scope = rememberCoroutineScope()
    val isDarkMode = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = modifier) {
        AndroidView(
            modifier = Modifier.weight(1f),
            onRelease = { it.release() },
            update = { info("Editor view update") },
            factory = { ctx ->
                Editor(ctx).apply {
                    info("New Editor instance")

                    editable = editorState.editable
                    val isTxtFile = file.getName().endsWith(".txt")
                    if (Settings.word_wrap_text && isTxtFile) {
                        setWordwrap(true, true, true)
                    }
                    id = View.generateViewId()
                    layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, 0)

                    setThemeColors(
                        isDarkMode = isDarkMode,
                        selectionColors = selectionColors,
                        colorScheme = colorScheme,
                    )

                    editorState.editor = WeakReference(this)

                    registerXedActions(scope, viewModel, this@CodeEditor)
                    registerXedEvents(this@CodeEditor, intelligentFeatures, file, onTextChange)

                    scope.launch(Dispatchers.IO) {
                        editorState.contentLoaded.await()
                        editorState.updateLock.withLock {
                            withContext(Dispatchers.Main) {
                                setText(editorState.content)
                                editorState.contentRendered.complete(Unit)
                            }
                        }
                    }

                    scope.launch { editorState.editorConfigLoaded?.await()?.let { applySettings() } }
                }
            },
        )

        if (Settings.show_extra_keys) {
            HorizontalDivider()
        }
    }
}

fun Editor.registerXedActions(scope: CoroutineScope, viewModel: MainViewModel, editorTab: EditorTab) {
    registerTextAction(
        TextActionItem(
            strings.open,
            drawables.open_in_new,
            shouldShow = { isUrlSelected() },
            onClick = {
                val text = getSelectedText() ?: return@TextActionItem
                val intent = Intent(Intent.ACTION_VIEW, text.toUri())
                context.startActivity(intent)
            },
        )
    )
    val lspActions = createLspTextActions(scope, context, viewModel, editorTab)
    lspActions.forEach { registerTextAction(it) }
}

fun Editor.registerXedEvents(
    editorTab: EditorTab,
    intelligentFeatures: List<IntelligentFeature>,
    file: FileObject,
    onTextChange: () -> Unit,
) {
    subscribeAlways(InlayHintClickEvent::class.java) { event ->
        val hint = event.inlayHint
        if (hint is ColorInlayHint) {
            val colorRange = hint.colorRange
            val indexedColorRange =
                colorRange?.let {
                    val startIndex = event.editor.text.getCharIndex(colorRange.start.line, colorRange.start.column)
                    val endIndex = event.editor.text.getCharIndex(colorRange.end.line, colorRange.end.column)
                    TextRange(
                        CharPosition(colorRange.start.line, colorRange.start.column, startIndex),
                        CharPosition(colorRange.end.line, colorRange.end.column, endIndex),
                    )
                }

            if (indexedColorRange != null) {
                val colorText = event.editor.text.substring(indexedColorRange.startIndex, indexedColorRange.endIndex)
                val colorValue = hint.color.resolve(colorScheme).let { Color(it) }
                val parsedColor = colorText.parseUnknownColor() ?: (colorValue to ColorFormat.HEX)

                editorTab.editorState.showColorPicker = parsedColor
                editorTab.editorState.colorPickerRange = indexedColorRange
            } else {
                toast(strings.invalid_color)
            }
        }
    }

    subscribeAlways(PublishDiagnosticsEvent::class.java) { event ->
        val viewModel = fileTreeViewModel.get()
        val diagnostics = event.newDiagnosticsEvent

        val highestSeverity = diagnostics.maxOfOrNull { it.severity.toInt() }

        if (highestSeverity != null) {
            viewModel?.diagnoseNode(file, highestSeverity)
        } else {
            viewModel?.undiagnoseNode(file)
        }
    }

    subscribeAlways(ContentChangeEvent::class.java) {
        intelligentFeatures.forEach { feature ->
            when (it.action) {
                ContentChangeEvent.ACTION_INSERT -> feature.handleInsert(this)
                ContentChangeEvent.ACTION_DELETE -> feature.handleDelete(this)
            }
        }

        if (it.changedText.length == 1) {
            val character = it.changedText.first()
            intelligentFeatures.forEach { feature ->
                if (feature.triggerCharacters.contains(character)) {
                    when (it.action) {
                        ContentChangeEvent.ACTION_INSERT -> feature.handleInsertChar(character, this)
                        ContentChangeEvent.ACTION_DELETE -> feature.handleDeleteChar(character, this)
                    }
                }
            }
        }

        if (!editorTab.editorState.updateLock.isLocked) {
            editorTab.editorState.updateUndoRedo()
            onTextChange.invoke()
        }
    }

    subscribeAlways(LayoutStateChangeEvent::class.java) { event ->
        editorTab.editorState.isWrapping = event.isLayoutBusy
    }

    subscribeAlways(EditorKeyEvent::class.java) { event ->
        intelligentFeatures.forEach { it.handleKeyEvent(event, this) }
    }

    // Intercept the default handling of some keybinds because
    // they should be handled by Xed-Editor's key binding system instead
    // (for custom keybinds support)
    subscribeAlways(KeyBindingEvent::class.java) { event ->
        intelligentFeatures.forEach { it.handleKeyBindingEvent(event, this) }
        if (event.isIntercepted) return@subscribeAlways

        val keyCode = event.keyCode
        val shouldBeIntercepted =
            keyCode == KeyEvent.KEYCODE_A ||
                keyCode == KeyEvent.KEYCODE_C ||
                keyCode == KeyEvent.KEYCODE_X ||
                keyCode == KeyEvent.KEYCODE_V ||
                keyCode == KeyEvent.KEYCODE_U ||
                keyCode == KeyEvent.KEYCODE_R ||
                keyCode == KeyEvent.KEYCODE_D ||
                keyCode == KeyEvent.KEYCODE_W ||
                keyCode == KeyEvent.KEYCODE_Y ||
                keyCode == KeyEvent.KEYCODE_Z ||
                keyCode == KeyEvent.KEYCODE_J
        if (shouldBeIntercepted) event.markAsConsumed()

        KeybindingsManager.handleEditorEvent(event, MainActivity.instance!!)
    }
}

fun EditorTab.applyHighlightingAndConnectLSP() {
    val editor = editorState.editor.get() ?: return

    with(editor) {
        scope.launch(Dispatchers.IO) {
            editorState.textmateScope?.let { setLanguage(it) }

            val builtin = getBuiltinServers(context)
            val extension = getExtensionServers(context)
            val external = getExternalServers()
            val servers = builtin + extension + external
            if (servers.isEmpty()) return@launch

            val wrapperLanguage =
                editorState.textmateScope?.let {
                    LanguageManager.createLanguage(textmateScope = it, createIdentifiers = false)
                }
            val projectFile =
                projectRoot
                    ?: run {
                        logWarn(
                            "File ${file.getName()} has no suitable project root. Skipping language server connection."
                        )
                        return@launch
                    }

            lspConnector =
                LspConnector(
                    projectFile = projectFile,
                    fileObject = file,
                    codeEditor = this@with,
                    editorTab = this@applyHighlightingAndConnectLSP,
                    servers = servers,
                )

            info("Trying to connect language servers...")
            lspConnector?.connect(wrapperLanguage)
            info("isConnected : ${lspConnector?.isConnected() ?: false}")
        }
    }
}

private suspend fun EditorTab.getBuiltinServers(context: Context): List<LspServer> {
    val servers = LspRegistry.builtInServer.filter { it.isSupported(file) }
    return findActiveLspServers(servers, context)
}

private fun EditorTab.promptLspInstall(context: Context, server: LspServer) {
    scope.launch {
        val snackbarHost = snackbarHostStateRef.get() ?: return@launch
        val result =
            snackbarHost.showSnackbar(
                message = strings.ask_lsp_install.getFilledString(server.languageName, context),
                actionLabel = strings.install.getString(),
                duration = SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed) {
            server.install(context)
        }
    }
}

private fun EditorTab.promptLspUpdate(context: Context, server: LspServer) {
    scope.launch {
        val snackbarHost = snackbarHostStateRef.get() ?: return@launch
        val result =
            snackbarHost.showSnackbar(
                message = strings.ask_lsp_update.getFilledString(server.languageName, context),
                actionLabel = strings.update.getString(),
                duration = SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed) {
            server.update(context)
        }
    }
}

private suspend fun EditorTab.getExtensionServers(context: Context): List<LspServer> {
    val servers = LspRegistry.extensionServers.filter { server -> server.isSupported(file) }
    return findActiveLspServers(servers, context)
}

private suspend fun EditorTab.findActiveLspServers(servers: List<LspServer>, context: Context): MutableList<LspServer> {
    val matchedServers = mutableListOf<LspServer>()

    servers.forEach { server ->
        if (!Preference.getBoolean("lsp_${server.id}", true)) {
            return@forEach
        }

        if (!server.isInstalled(context)) {
            info("Server ${server.id} is not installed")
            promptLspInstall(context, server)
            return@forEach
        }

        scope.launch(Dispatchers.IO) {
            if (server.isUpdatable(context)) {
                info("Server ${server.id} is updatable")
                promptLspUpdate(context, server)
            }
        }

        matchedServers.add(server)
        return@forEach
    }

    return matchedServers
}

private fun EditorTab.getExternalServers(): List<LspServer> {
    return LspRegistry.externalServers.filter { server -> server.isSupported(file) }
}
