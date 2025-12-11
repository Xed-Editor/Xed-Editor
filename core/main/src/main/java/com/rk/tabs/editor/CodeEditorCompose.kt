package com.rk.tabs.editor

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
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
import com.rk.editor.Editor
import com.rk.editor.getInputView
import com.rk.exec.isTerminalInstalled
import com.rk.exec.isTerminalWorking
import com.rk.file.FileType
import com.rk.lsp.BaseLspConnector
import com.rk.lsp.builtInServer
import com.rk.lsp.createLspTextActions
import com.rk.lsp.externalServers
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.settings.app.InbuiltFeatures
import com.rk.utils.dialog
import com.rk.utils.dpToPx
import com.rk.utils.info
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
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
    onKeyEvent: (EditorKeyEvent) -> Unit,
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

                val horizontalScrollViewId = View.generateViewId()
                val dividerId = View.generateViewId()

                val editor =
                    Editor(ctx).apply {
                        info("New Editor instance")

                        editable = state.editable
                        if (Settings.word_wrap_for_text && !isWordwrap) {
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

                        subscribeAlways(ContentChangeEvent::class.java) {
                            if (!state.updateLock.isLocked) {
                                state.isDirty = true
                                editorState.updateUndoRedo()
                                onTextChange.invoke()
                            }
                        }

                        subscribeAlways(EditorKeyEvent::class.java) { event -> onKeyEvent.invoke(event) }

                        subscribeAlways(LayoutStateChangeEvent::class.java) { event ->
                            editorState.isWrapping = event.isLayoutBusy
                        }

                        applyHighlightingAndConnectLSP()
                    }

                val keyPanel =
                    HorizontalScrollView(ctx).apply {
                        state.arrowKeys = WeakReference(this)
                        id = horizontalScrollViewId

                        visibility =
                            if (Settings.show_extra_keys) {
                                View.VISIBLE
                            } else {
                                View.GONE
                            }

                        layoutParams =
                            ConstraintLayout.LayoutParams(
                                ConstraintLayout.LayoutParams.MATCH_PARENT,
                                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                            )
                        isHorizontalScrollBarEnabled = false
                        isSaveEnabled = false
                        addView(getInputView(editor, realSurface.toArgb(), onSurfaceColor.toArgb(), viewModel))
                    }

                val divider =
                    View(ctx).apply {
                        id = dividerId
                        visibility =
                            if (Settings.show_extra_keys) {
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
                addView(keyPanel)

                with(constraintSet) {
                    clone(this@apply)

                    connect(editor.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(editor.id, ConstraintSet.BOTTOM, dividerId, ConstraintSet.TOP) // Connect to divider top

                    connect(dividerId, ConstraintSet.TOP, editor.id, ConstraintSet.BOTTOM)
                    connect(dividerId, ConstraintSet.BOTTOM, horizontalScrollViewId, ConstraintSet.TOP)
                    connect(dividerId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(dividerId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                    connect(horizontalScrollViewId, ConstraintSet.TOP, dividerId, ConstraintSet.BOTTOM)
                    connect(horizontalScrollViewId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                    connect(editor.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(editor.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    connect(horizontalScrollViewId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(horizontalScrollViewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

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

                info("Attempting to connect to external server...")
                if (tryConnectExternalLsp()) return@launch
                info("No external server connection")

                info("Attempting to connect to built-in server...")

                if (tryConnectBuiltinLsp(ext, this@with)) {
                    info("LSP Server connected")
                    return@launch
                } else {
                    info("No builtin server connection")
                }
            }
        }
    }
}

private suspend fun EditorTab.tryConnectBuiltinLsp(ext: String, editor: Editor): Boolean {
    val server = builtInServer.find { it.supportedExtensions.map { e -> e.lowercase() }.contains(ext.lowercase()) }
    if (server != null && Preference.getBoolean("lsp_${server.id}", true)) {
        if (!InbuiltFeatures.terminal.state.value || !isTerminalInstalled() || !isTerminalWorking()) {
            return false
        }

        // Connect with built-in language server
        if (server.isInstalled(editor.context)) {
            info("Server installed")

            if (server.isSupported(file).not()) {
                info("This server: ${server.serverName} does not support this file")
                return false
            }

            val parentFile =
                file.getParentFile()
                    ?: run {
                        info("File has no parent directory")
                        return false
                    }

            baseLspConnector =
                BaseLspConnector(
                    parentFile,
                    fileObject = file,
                    codeEditor = editorState.editor.get()!!,
                    server = server,
                )

            file.getParentFile()?.let {
                info("Trying to connect")
                baseLspConnector?.connect(FileType.fromExtension(ext).textmateScope!!)

                info("isConnected : ${baseLspConnector?.isConnected() ?: false}")
            } ?: info("No parent")

            return true
        }

        if (editorState.lspDialogMutex.isLocked.not()) {
            editorState.lspDialogMutex.lock()
            dialog(
                context = editor.context as Activity,
                title = strings.attention.getString(editor.context),
                msg = strings.ask_lsp_install.getFilledString(editor.context, server.languageName),
                cancelString = strings.disable,
                okString = strings.install,
                onOk = {
                    if (editorState.lspDialogMutex.isLocked) {
                        editorState.lspDialogMutex.unlock()
                    }
                    server.install(editor.context)
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
    return false
}

private suspend fun EditorTab.tryConnectExternalLsp(): Boolean {
    val parent = file.getParentFile() ?: return false

    externalServers.forEach { server ->
        if (server.isSupported(file)) {
            baseLspConnector =
                BaseLspConnector(parent, fileObject = file, codeEditor = editorState.editor.get()!!, server = server)

            baseLspConnector?.connect(editorState.textmateScope!!)
            return true
        } else {
            info("Server \"${server.serverName}\" does not support this file")
        }
    }

    return false
}
