package com.rk.tabs.editor

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import com.rk.activities.main.MainActivity
import com.rk.activities.main.fileTreeViewModel
import com.rk.activities.main.snackbarHostStateRef
import com.rk.commands.KeybindingsManager
import com.rk.editor.Editor
import com.rk.editor.intelligent.IntelligentFeature
import com.rk.lsp.BaseLspConnector
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspRegistry
import com.rk.lsp.createLspTextActions
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.ReactiveSettings
import com.rk.settings.Settings
import com.rk.utils.dpToPx
import com.rk.utils.info
import com.rk.utils.logWarn
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
    intelligentFeatures: List<IntelligentFeature>,
    onTextChange: () -> Unit,
) {
    val selectionColors = LocalTextSelectionColors.current
    val isDarkMode = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme

    val divider = colorScheme.outlineVariant
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

                        state.editor = WeakReference(this)

                        val lspActions = createLspTextActions(scope, context, viewModel, this@CodeEditor)
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
    val editor = editorState.editor.get() ?: return

    with(editor) {
        editorState.textmateScope?.let { langScope ->
            scope.launch(Dispatchers.IO) {
                setLanguage(langScope)

                val builtin = getBuiltinServers(context)
                val extension = getExtensionServers(context)
                val external = getExternalServers()
                val servers = builtin + extension + external
                if (servers.isEmpty()) return@launch

                val projectFile =
                    projectRoot
                        ?: run {
                            logWarn(
                                "File ${file.getName()} has no suitable project root. Skipping language server connection."
                            )
                            return@launch
                        }

                baseLspConnector =
                    BaseLspConnector(
                        projectFile = projectFile,
                        fileObject = file,
                        codeEditor = this@with,
                        editorTab = this@applyHighlightingAndConnectLSP,
                        servers = servers,
                    )

                info("Trying to connect language servers...")
                baseLspConnector?.connect(langScope)
                info("isConnected : ${baseLspConnector?.isConnected() ?: false}")
            }
        }
    }
}

private fun EditorTab.getBuiltinServers(context: Context): List<BaseLspServer> {
    val servers = LspRegistry.builtInServer.filter { it.isSupported(file) }
    return findActiveLspServers(servers, context)
}

private fun EditorTab.promptLspInstall(context: Context, server: BaseLspServer) {
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

private fun EditorTab.getExtensionServers(context: Context): List<BaseLspServer> {
    val servers = LspRegistry.extensionServers.filter { server -> server.isSupported(file) }
    return findActiveLspServers(servers, context)
}

private fun EditorTab.findActiveLspServers(servers: List<BaseLspServer>, context: Context): MutableList<BaseLspServer> {
    val matchedServers = mutableListOf<BaseLspServer>()

    servers.forEach { server ->
        if (!Preference.getBoolean("lsp_${server.id}", true)) {
            return@forEach
        }

        if (!server.isInstalled(context)) {
            info("Server ${server.id} is not installed")
            promptLspInstall(context, server)
            return@forEach
        }

        matchedServers.add(server)
        return@forEach
    }

    return matchedServers
}

private fun EditorTab.getExternalServers(): List<BaseLspServer> {
    return LspRegistry.externalServers.filter { server -> server.isSupported(file) }
}
