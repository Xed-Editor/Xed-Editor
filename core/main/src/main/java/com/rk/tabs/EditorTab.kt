package com.rk.tabs

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import com.rk.DefaultScope
import com.rk.activities.main.EditorCursorState
import com.rk.activities.main.EditorTabState
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.TabState
import com.rk.components.AddDialogItem
import com.rk.components.CodeItem
import com.rk.components.EditorActions
import com.rk.components.FindingsDialog
import com.rk.components.SearchPanel
import com.rk.components.SingleInputDialog
import com.rk.components.SyntaxPanel
import com.rk.editor.Editor
import com.rk.editor.getInputView
import com.rk.exec.isTerminalInstalled
import com.rk.exec.isTerminalWorking
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.lsp.BaseLspConnector
import com.rk.lsp.createLspTextActions
import com.rk.lsp.formatDocumentSuspend
import com.rk.lsp.builtInServer
import com.rk.lsp.externalServers
import com.rk.resources.drawables
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.RunnerImpl
import com.rk.runner.currentRunner
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.settings.app.InbuiltFeatures
import com.rk.utils.dialog
import com.rk.utils.dpToPx
import com.rk.utils.errorDialog
import com.rk.utils.info
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.nio.charset.Charset

data class CodeEditorState(
    val initialContent: Content? = null,
) {
    var editor: WeakReference<Editor?> = WeakReference(null)
    var arrowKeys: WeakReference<HorizontalScrollView?> = WeakReference(null)
    var rootView: WeakReference<ConstraintLayout?> = WeakReference(null)

    var content by mutableStateOf(initialContent)
    var isDirty by mutableStateOf(false)
    var editable by mutableStateOf(Settings.read_only_default.not())
    val updateLock = Mutex()
    val contentLoaded = CompletableDeferred<Unit>()
    val contentRendered = CompletableDeferred<Unit>()

    var isSearching by mutableStateOf(false)
    var isReplaceShown by mutableStateOf(false)
    var ignoreCase by mutableStateOf(true)
    var searchRegex by mutableStateOf(false)
    var searchWholeWord by mutableStateOf(false)
    var showOptionsMenu by mutableStateOf(false)
    var searchKeyword by mutableStateOf("")
    var replaceKeyword by mutableStateOf("")

    var showSyntaxPanel by mutableStateOf(false)

    var showFindingsDialog by mutableStateOf(false)
    var findingsItems by mutableStateOf(listOf<CodeItem>())
    var findingsTitle by mutableStateOf("")
    var findingsDescription by mutableStateOf("")

    var showRenameDialog by mutableStateOf(false)
    var renameValue by mutableStateOf("")
    var renameError by mutableStateOf<String?>(null)
    var renameConfirm by mutableStateOf<((String) -> Unit)?>(null)

    var textmateScope by mutableStateOf<String?>(null)

    var runnersToShow by mutableStateOf<List<RunnerImpl>>(emptyList())
    var showRunnerDialog by mutableStateOf(false)

    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    fun updateUndoRedo() {
        canUndo = editor.get()?.canUndo() ?: false
        canRedo = editor.get()?.canRedo() ?: false
    }

    val lspDialogMutex by lazy { Mutex() }
    var isWrapping by mutableStateOf(false)
}

@OptIn(DelicateCoroutinesApi::class)
class EditorTab(
    override var file: FileObject,
    val viewModel: MainViewModel,
) : Tab() {

    private val charset = Charset.forName(Settings.encoding)
    var baseLspConnector: BaseLspConnector? = null

    override val icon: ImageVector
        get() = Icons.Outlined.Edit

    override val name: String
        get() = strings.editor.getString()

    val scope = CoroutineScope(Dispatchers.Default)

    override var tabTitle: MutableState<String> = mutableStateOf(file.getName()).also {
        scope.launch(Dispatchers.IO) {
            delay(100)
            val parent = file.getParentFile()
            if (viewModel.tabs.any { it.tabTitle.value == tabTitle.value && it != this@EditorTab } && parent != null) {

                val title = "${parent.getName()}/${tabTitle.value}"
                withContext(Dispatchers.Main) {
                    tabTitle.value = title
                }
            }
        }
    }

    val editorState by mutableStateOf(CodeEditorState())

    override fun onTabRemoved() {
        scope.cancel()
        editorState.content = null
        editorState.arrowKeys = WeakReference(null)
        editorState.editor.get()?.setText("")
        editorState.editor.get()?.release()
        GlobalScope.launch(Dispatchers.IO) {
            baseLspConnector?.disconnect()
        }
    }

    init {
        editorState.editable = Settings.read_only_default.not() && file.canWrite()
        if (editorState.textmateScope == null) {
            editorState.textmateScope = file.let {
                val ext = it.getName().substringAfterLast('.', "")
                FileType.fromExtension(ext).textmateScope
            }
        }
        if (editorState.content == null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    editorState.content = file.getInputStream().use {
                        ContentIO.createFrom(it, charset)
                    }
                    editorState.contentLoaded.complete(Unit)
                }.onFailure {
                    errorDialog(it)
                }
            }
        }
    }

    private val saveMutex = Mutex()

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun save() = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            if (Settings.format_on_save && baseLspConnector?.isFormattingSupported() == true) {
                formatDocumentSuspend(this@EditorTab)
            }

            runCatching {
                if (file.canWrite().not()) {
                    errorDialog(strings.cant_write)
                    return@withContext
                }
                editorState.isDirty = false
                file.writeText(editorState.content.toString(), charset)
                editorState.isDirty = false
                baseLspConnector?.notifySave(charset)
            }.onFailure {
                errorDialog(it)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current

        key(refreshKey) {
            LaunchedEffect(editorState.editable) {
                editorState.editor.get()?.editable = editorState.editable
            }

            Column {
                if (editorState.showRunnerDialog) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            editorState.showRunnerDialog = false
                            editorState.runnersToShow = emptyList()
                        },
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp,
                                top = 0.dp
                            )
                        ) {
                            editorState.runnersToShow.forEach { runner ->
                                AddDialogItem(
                                    icon = drawables.run,
                                    title = runner.getName()
                                ) {
                                    DefaultScope.launch {
                                        currentRunner = WeakReference(runner)
                                        runner.run(context, file)
                                        editorState.showRunnerDialog = false
                                        editorState.runnersToShow = emptyList()
                                    }
                                }
                            }
                        }
                    }
                }

                if (editorState.showSyntaxPanel) {
                    SyntaxPanel(
                        onDismissRequest = { editorState.showSyntaxPanel = false },
                        editorState = editorState
                    )
                }

                if (editorState.showFindingsDialog) {
                    FindingsDialog(
                        title = editorState.findingsTitle,
                        codeItems = editorState.findingsItems,
                        description = editorState.findingsDescription,
                        onFinish = {
                            editorState.showFindingsDialog = false
                        }
                    )
                }

                if (editorState.showRenameDialog) {
                    SingleInputDialog(
                        title = stringResource(strings.rename_symbol),
                        inputLabel = stringResource(strings.new_name),
                        inputValue = editorState.renameValue,
                        errorMessage = editorState.renameError,
                        confirmEnabled = editorState.renameValue.isNotBlank(),
                        onInputValueChange = {
                            editorState.renameValue = it
                            editorState.renameError = null
                            if (editorState.renameValue.isBlank()) {
                                editorState.renameError = strings.name_empty_err.getString()
                            }
                        },
                        onConfirm = {
                            editorState.renameConfirm?.let { it(editorState.renameValue) }
                        },
                        onFinish = {
                            editorState.renameValue = ""
                            editorState.renameError = null
                            editorState.renameConfirm = null
                            editorState.showRenameDialog = false
                        }
                    )
                }

                SearchPanel(editorState = editorState)
                if (editorState.isSearching) {
                    HorizontalDivider()
                }

                if (editorState.isWrapping){
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Butt
                    )
                }

                CodeEditor(
                    modifier = Modifier,
                    state = editorState,
                    parentTab = this@EditorTab,
                    onTextChange = {
                        if (Settings.auto_save) {
                            scope.launch(Dispatchers.IO) {
                                save()
                                saveMutex.lock()
                                delay(400)
                                saveMutex.unlock()
                            }
                        }
                    },
                    onKeyEvent = { event ->
                        if (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_S) {
                            scope.launch(Dispatchers.IO) {
                                save()
                            }
                        }
                    }
                )

                LaunchedEffect(
                    editorState.textmateScope,
                    refreshKey,
                    LocalConfiguration.current,
                    LocalContext.current,
                    MaterialTheme.colorScheme
                ) {
                    applyHighlighting()
                }

            }
        }
    }

    override fun getState(): TabState? {
        val editor = editorState.editor.get() ?: return null
        return EditorTabState(
            fileObject = file,
            cursor = EditorCursorState(
                lineLeft = editor.cursor.leftLine,
                columnLeft = editor.cursor.leftColumn,
                lineRight = editor.cursor.rightLine,
                columnRight = editor.cursor.rightColumn
            ),
            scrollX = editor.scrollX,
            scrollY = editor.scrollY,
            unsavedContent = if (editorState.isDirty) editor.text.toString() else null
        )
    }

    @Composable
    override fun RowScope.Actions() {
        EditorActions(
            modifier = Modifier,
            viewModel = viewModel
        )
    }

    override val showGlobalActions: Boolean = false

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val content = file.getInputStream().use {
                ContentIO.createFrom(it)
            }
            editorState.content = content
            withContext(Dispatchers.Main) {
                editorState.updateLock.withLock {
                    editorState.editor.get()?.setText(content)
                    editorState.updateUndoRedo()
                }
            }
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
@Composable
private fun EditorTab.CodeEditor(
    modifier: Modifier = Modifier,
    state: CodeEditorState,
    parentTab: EditorTab,
    onKeyEvent: (EditorKeyEvent) -> Unit,
    onTextChange: () -> Unit
) {
    val surfaceColor = if (isSystemInDarkTheme()) {
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
        onRelease = {
            it.children.filterIsInstance<Editor>().firstOrNull()?.release()
        },
        update = {
            info("Editor view update")
        },
        factory = { ctx ->
            ConstraintLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                val horizontalScrollViewId = View.generateViewId()
                val dividerId = View.generateViewId()

                val editor = Editor(ctx).apply {
                    info("New Editor instance")

                    editable = state.editable
                    if (Settings.word_wrap_for_text && !isWordwrap) {
                        isWordwrap = file.getName().endsWith(".txt")
                    }
                    id = View.generateViewId()
                    layoutParams = ConstraintLayout.LayoutParams(
                        ConstraintLayout.LayoutParams.MATCH_PARENT,
                        0
                    )

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
                        dividerColor = divider.toArgb()
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

                    subscribeAlways(EditorKeyEvent::class.java) { event ->
                        onKeyEvent.invoke(event)
                    }


                    subscribeAlways(LayoutStateChangeEvent::class.java){ event ->
                        editorState.isWrapping = event.isLayoutBusy
                    }

                    applyHighlighting()
                }

                val keyPanel = HorizontalScrollView(ctx).apply {
                    state.arrowKeys = WeakReference(this)
                    id = horizontalScrollViewId

                    visibility = if (Settings.show_extra_keys) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }

                    layoutParams = ConstraintLayout.LayoutParams(
                        ConstraintLayout.LayoutParams.MATCH_PARENT,
                        ConstraintLayout.LayoutParams.WRAP_CONTENT
                    )
                    isHorizontalScrollBarEnabled = false
                    isSaveEnabled = false
                    addView(
                        getInputView(
                            editor,
                            realSurface.toArgb(),
                            onSurfaceColor.toArgb(),
                            viewModel
                        )
                    )
                }

                val divider = View(ctx).apply {
                    id = dividerId
                    visibility = if (Settings.show_extra_keys) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                    layoutParams = ConstraintLayout.LayoutParams(
                        ConstraintLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(1f, ctx)
                    ).apply {
                        setBackgroundColor(divider.toArgb())
                    }
                }

                addView(editor)
                addView(divider)
                addView(keyPanel)

                with(constraintSet) {
                    clone(this@apply)

                    connect(
                        editor.id,
                        ConstraintSet.TOP,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.TOP
                    )
                    connect(
                        editor.id,
                        ConstraintSet.BOTTOM,
                        dividerId,
                        ConstraintSet.TOP
                    ) // Connect to divider top

                    connect(dividerId, ConstraintSet.TOP, editor.id, ConstraintSet.BOTTOM)
                    connect(
                        dividerId,
                        ConstraintSet.BOTTOM,
                        horizontalScrollViewId,
                        ConstraintSet.TOP
                    )
                    connect(
                        dividerId,
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START
                    )
                    connect(
                        dividerId,
                        ConstraintSet.END,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.END
                    )

                    connect(
                        horizontalScrollViewId,
                        ConstraintSet.TOP,
                        dividerId,
                        ConstraintSet.BOTTOM
                    )
                    connect(
                        horizontalScrollViewId,
                        ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.BOTTOM
                    )

                    connect(
                        editor.id,
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START
                    )
                    connect(
                        editor.id,
                        ConstraintSet.END,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.END
                    )
                    connect(
                        horizontalScrollViewId,
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START
                    )
                    connect(
                        horizontalScrollViewId,
                        ConstraintSet.END,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.END
                    )

                    applyTo(this@apply)
                }
                editorState.rootView = WeakReference(this)

            }
        },
    )

}

fun EditorTab.applyHighlighting() {
    if (editorState.editor.get() == null) return

    with(editorState.editor.get()!!) {
        editorState.textmateScope?.let { langScope ->
            scope.launch(Dispatchers.IO) {
                setLanguage(langScope)
                applyMarkdownHighlighting()

                if (!InbuiltFeatures.terminal.state.value || !isTerminalInstalled() || !isTerminalWorking()) {
                    if (editorState.lspDialogMutex.isLocked) return@launch
                    editorState.lspDialogMutex.lock()
                    dialog(
                        context = context as Activity,
                        title = strings.warning.getString(context),
                        msg = strings.lsp_terminal_unavailable.getString(context),
                        okString = strings.ok,
                    )
                    return@launch
                }
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

private suspend fun EditorTab.tryConnectBuiltinLsp(
    ext: String,
    editor: Editor,
): Boolean {
    val server = builtInServer.find {
        it.supportedExtensions.map { e -> e.lowercase() }
            .contains(ext.lowercase())
    }
    if (server != null && Preference.getBoolean("lsp_${server.id}", true)) {
        // Connect with built-in language server
        if (server.isInstalled(editor.context)) {
            info("Server installed")

            if (server.isSupported(file).not()){
                info("This server: ${server.serverName} does not support this file")
                return false
            }

            val parentFile = file.getParentFile() ?: run {
                info("File has no parent directory")
                return false
            }

            baseLspConnector = BaseLspConnector(
                parentFile,
                fileObject = file,
                codeEditor = editorState.editor.get()!!,
                server = server
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
                msg = strings.ask_lsp_install.getFilledString(
                    editor.context,
                    server.languageName
                ),
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
                    Preference.setBoolean(
                        "lsp_${server.id}",
                        false
                    )
                }
            )
        }
    }
    return false
}

private suspend fun EditorTab.tryConnectExternalLsp(): Boolean {
    val parent = file.getParentFile() ?: return false

    externalServers.forEach { server ->
        if (server.isSupported(file)){
            baseLspConnector = BaseLspConnector(
                parent,
                fileObject = file,
                codeEditor = editorState.editor.get()!!,
                server = server
            )

            baseLspConnector?.connect(editorState.textmateScope!!)
            return true
        }else{
            info("Server \"${server.serverName}\" does not support this file")
        }
    }

    return false
}