package com.rk.tabs.editor

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import com.rk.activities.main.MainActivity
import com.rk.activities.main.fileTreeViewModel
import com.rk.commands.KeybindingsManager
import com.rk.editor.Editor
import com.rk.editor.intelligent.IntelligentFeature
import com.rk.file.FileType
import com.rk.lsp.BaseLspConnector
import com.rk.lsp.BaseLspServer
import com.rk.lsp.builtInServer
import com.rk.lsp.createLspTextActions
import com.rk.lsp.externalServers
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.ReactiveSettings
import com.rk.settings.Settings
import com.rk.utils.dialog
import com.rk.utils.dpToPx
import com.rk.utils.info
import com.rk.utils.logError
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.event.PublishDiagnosticsEvent
import java.lang.ref.WeakReference
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun EditorTab.CodeEditor(
    modifier: Modifier = Modifier,
    state: CodeEditorState,
    parentTab: EditorTab,
    intelligentFeatures: List<IntelligentFeature>,
    onTextChange: () -> Unit,
) {
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
    val currentLineColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)

    val divider = MaterialTheme.colorScheme.outlineVariant
    val errorColor = MaterialTheme.colorScheme.error
    val isDarkMode = isSystemInDarkTheme()

    val constraintSet = remember { ConstraintSet() }
    val scope = rememberCoroutineScope()

    AndroidView(
        modifier = modifier.fillMaxSize(),
        onRelease = { it.children.filterIsInstance<Editor>().firstOrNull()?.release() },
        update = { info("Editor view update") },
        factory = { ctx ->
            ConstraintLayout(ctx).apply {
                layoutParams =
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                val dividerId = View.generateViewId()

                val editor =
                    Editor(ctx).apply {
                        if (this@CodeEditor == viewModel.currentTab) {
                            requestFocus()
                            requestFocusFromTouch()
                        }

                        info("New Editor instance")

                        editable = state.editable
                        if (Settings.word_wrap_text && !isWordwrap) {
                            val isTextFile = file.getName().endsWith(".txt")
                            setWordwrap(isTextFile, true, true)
                        }
                        id = View.generateViewId()
                        layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, 0)

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
                            errorColor = errorColor.toArgb(),
                        )

                        state.editor = WeakReference(this)

                        val lspActions = createLspTextActions(scope, context, viewModel, parentTab)
                        lspActions.forEach { registerTextAction(it) }

                        scope.launch(Dispatchers.IO) {
                            state.contentLoaded.await()
                            state.updateLock.withLock {
                                withContext(Dispatchers.Main) {
                                    setText(state.content)
                                    state.contentRendered.complete(Unit)
                                }
                            }
                        }

                        scope.launch { state.editorConfigLoaded?.await()?.let { applySettings() } }

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
                                            ContentChangeEvent.ACTION_INSERT ->
                                                feature.handleInsertChar(character, this)
                                            ContentChangeEvent.ACTION_DELETE ->
                                                feature.handleDeleteChar(character, this)
                                        }
                                    }
                                }
                            }

                            if (!state.updateLock.isLocked) {
                                state.isDirty = true
                                editorState.updateUndoRedo()
                                onTextChange.invoke()
                            }
                        }

                        subscribeAlways(LayoutStateChangeEvent::class.java) { event ->
                            editorState.isWrapping = event.isLayoutBusy
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

                val divider =
                    View(ctx).apply {
                        id = dividerId
                        visibility =
                            if (ReactiveSettings.showExtraKeys) {
                                View.VISIBLE
                            } else {
                                View.GONE
                            }
                        layoutParams =
                            ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, dpToPx(1f, ctx))
                                .apply { setBackgroundColor(divider.toArgb()) }
                    }

                addView(editor)
                addView(divider)

                with(constraintSet) {
                    clone(this@apply)

                    connect(editor.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(editor.id, ConstraintSet.BOTTOM, dividerId, ConstraintSet.TOP) // Connect to divider top
                    connect(editor.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(editor.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                    connect(dividerId, ConstraintSet.TOP, editor.id, ConstraintSet.BOTTOM)
                    connect(dividerId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                    connect(dividerId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(dividerId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                    applyTo(this@apply)
                }
                editorState.rootView = WeakReference(this)
            }
        },
    )
}

fun EditorTab.applyHighlightingAndConnectLSP() {
    if (editorState.editor.get() == null) return

    with(editorState.editor.get()!!) {
        editorState.textmateScope?.let { langScope ->
            scope.launch(Dispatchers.IO) {
                setLanguage(langScope)
                applyMarkdownHighlighting()

                val ext = file.getName().substringAfterLast(".").trim()

                val builtin = getBuiltinServers(ext, context)
                val external = getExternalServers()
                if (builtin.isEmpty() && external.isEmpty()) return@launch

                val parentFile =
                    file.getParentFile()
                        ?: run {
                            info("File has no parent directory")
                            return@launch
                        }

                baseLspConnector =
                    BaseLspConnector(
                        projectFile = parentFile,
                        fileObject = file,
                        codeEditor = editorState.editor.get()!!,
                        servers = external + builtin,
                    )

                parentFile.let {
                    info("Trying to connect...")
                    val textMateScope = FileType.getTextmateScopeFromName(file.getName())
                    if (textMateScope != null) {
                        baseLspConnector?.connect(textMateScope)
                    } else {
                        logError("TextMate scope is null")
                    }
                    info("isConnected : ${baseLspConnector?.isConnected() ?: false}")
                }
            }
        }
    }
}

private suspend fun EditorTab.getBuiltinServers(ext: String, context: Context): List<BaseLspServer> {
    val servers = builtInServer.filter { it.supportedExtensions.map { e -> e.lowercase() }.contains(ext.lowercase()) }
    val supportedServers = mutableListOf<BaseLspServer>()

    servers.forEach { server ->
        if (!Preference.getBoolean("lsp_${server.id}", true)) {
            return@forEach
        }

        if (!server.isInstalled(context)) {
            info("Server is not installed")
            showServerInstallDialog(context, server)
            return@forEach
        }

        if (!server.isSupported(file)) {
            info("This server: ${server.serverName} does not support this file")
            return@forEach
        }

        supportedServers.add(server)
        return@forEach
    }

    return supportedServers
}

private suspend fun EditorTab.showServerInstallDialog(context: Context, server: BaseLspServer) {
    if (!editorState.lspDialogMutex.isLocked) {
        editorState.lspDialogMutex.lock()
        dialog(
            context = context as Activity,
            title = strings.attention.getString(context),
            msg = strings.ask_lsp_install.getFilledString(context, server.languageName),
            cancelString = strings.disable,
            okString = strings.install,
            onOk = {
                if (editorState.lspDialogMutex.isLocked) {
                    editorState.lspDialogMutex.unlock()
                }
                server.install(context)
            },
            onCancel = {
                if (editorState.lspDialogMutex.isLocked) {
                    editorState.lspDialogMutex.unlock()
                }
                Preference.setBoolean("lsp_${server.id}", false)
            },
        )
    }
}

private fun EditorTab.getExternalServers(): List<BaseLspServer> {
    return externalServers.filter { server -> server.isSupported(file) }
}
