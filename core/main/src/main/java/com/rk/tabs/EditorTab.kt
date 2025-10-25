package com.rk.tabs

import android.app.Activity
import com.rk.xededitor.ui.activities.main.ControlPanel
import com.rk.xededitor.ui.activities.main.MainViewModel
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import com.rk.file.FileObject
import com.rk.libcommons.dialog
import com.rk.libcommons.dpToPx
import com.rk.libcommons.editor.BaseLspConnector
import com.rk.libcommons.editor.Editor
import com.rk.libcommons.editor.getInputView
import com.rk.libcommons.editor.lspRegistry
import com.rk.libcommons.editor.textmateSources
import com.rk.libcommons.errorDialog
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.terminal.ProcessConnection
import com.rk.xededitor.ui.components.EditorActions
import com.rk.xededitor.ui.components.SearchPanel
import com.rk.xededitor.ui.components.updateUndoRedo
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
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
import java.nio.charset.Charset
import com.rk.libcommons.editor.createLspTextActions
import com.rk.xededitor.ui.components.CodeItem
import com.rk.xededitor.ui.components.FindingsDialog
import com.rk.xededitor.ui.components.SingleInputDialog
import kotlinx.coroutines.CompletableDeferred


data class CodeEditorState(
    val initialContent: Content? = null,
) {
    var editor: Editor? = null
    var arrowKeys: HorizontalScrollView? = null
    var content by mutableStateOf(initialContent)
    var isDirty by mutableStateOf(false)
    var editable by mutableStateOf(false)
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

    var showControlPanel by mutableStateOf(false)

    var showFindingsDialog by mutableStateOf(false)
    var findingsItems by mutableStateOf(listOf<CodeItem>())
    var findingsTitle by mutableStateOf("")
    var findingsDescription by mutableStateOf("")

    var showRenameDialog by mutableStateOf(false)
    var renameValue by mutableStateOf("")
    var renameError by mutableStateOf<String?>(null)
    var renameConfirm by mutableStateOf<((String) -> Unit)?>(null)
}

val lsp_connections = mutableMapOf<String, Int>()

@OptIn(DelicateCoroutinesApi::class)
class EditorTab(
    var file: FileObject,
    val viewModel: MainViewModel,
) : Tab() {

    private val charset = Charset.forName(Settings.encoding)
    var lspConnection: StreamConnectionProvider? = null
    var baseLspConnector: BaseLspConnector? = null

    override val icon: ImageVector
        get() = Icons.Outlined.Edit

    override val name: String
        get() = strings.editor.getString()

    val scope = CoroutineScope(Dispatchers.Default)

    override var tabTitle: MutableState<String> = mutableStateOf(file.getName()).also {
        scope.launch{
            delay(100)
            val parent = file.getParentFile()
            if (viewModel.tabs.any { it.tabTitle.value == tabTitle.value && it != this@EditorTab } && parent != null){

                val title = "${parent.getName()}/${tabTitle.value}"
                withContext(Dispatchers.Main){
                    tabTitle.value = title
                }

            }
        }
    }

    val editorState by mutableStateOf(CodeEditorState())

    override fun onTabRemoved() {
        scope.cancel()
        editorState.content = null
        editorState.arrowKeys = null
        editorState.editor?.setText("")
        editorState.editor?.release()
        GlobalScope.launch{
            baseLspConnector?.disconnect()
            lspConnection?.close()
        }
    }

    init {
        if (editorState.content == null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    editorState.content = file.getInputStream().use {
                        ContentIO.createFrom(it, charset)
                    }
                    editorState.editable = Settings.read_only_default.not() && file.canWrite()
                    editorState.contentLoaded.complete(Unit)
                }.onFailure {
                    errorDialog(it)
                }
            }
        }
    }

    private val saveMutex = Mutex()

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun save() = withContext(Dispatchers.IO){
        saveMutex.withLock{
            runCatching {
                if (file.canWrite().not()){
                    errorDialog(strings.cant_write)
                    return@withContext
                }
                editorState.isDirty = false
                file.writeText(editorState.content.toString(),charset)
                editorState.isDirty = false
                baseLspConnector?.notifySave(charset)
            }.onFailure {
                errorDialog(it)
            }
        }
    }

    @Composable
    override fun Content(){
        Column {
            val language = file.let {
                textmateSources[it.getName().substringAfterLast('.', "").trim()]
            }

            if (editorState.showControlPanel){
                ControlPanel(onDismissRequest = {
                    editorState.showControlPanel = false
                }, viewModel = viewModel)
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
            if (editorState.isSearching){
                HorizontalDivider()
            }

            CodeEditor(
                modifier = Modifier,
                state = editorState,
                textmateScope = language,
                onTextChange = {
                    if (Settings.auto_save){
                        scope.launch(Dispatchers.IO){
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
        }
    }

    @Composable
    override fun RowScope.Actions(){
        EditorActions(
            modifier = Modifier,
            tab = this@EditorTab,
            viewModel = viewModel
        )
    }

    fun refresh(){
        scope.launch(Dispatchers.IO){
            val content = file.getInputStream().use {
                ContentIO.createFrom(it)
            }
            editorState.content = content
            withContext(Dispatchers.Main){
                editorState.updateLock.withLock{
                    editorState.editor?.setText(content)
                    editorState.editor!!.updateUndoRedo()
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
    textmateScope: String? = null,
    onKeyEvent: (EditorKeyEvent) -> Unit,
    onTextChange: () -> Unit
) {
    val surfaceColor = if (isSystemInDarkTheme()){ MaterialTheme.colorScheme.surfaceDim }else{ MaterialTheme.colorScheme.surface }
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
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
    val currentLineColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.8f)

    val divider = MaterialTheme.colorScheme.outlineVariant


    AnimatedVisibility(visible = true) {
        val constraintSet = remember { ConstraintSet() }
        val scope = rememberCoroutineScope()

        AndroidView(
            modifier = modifier.fillMaxSize(),
            onRelease = {
                it.children.filterIsInstance<Editor>().firstOrNull()?.release()
            },
            update = {},
            factory = { ctx ->
                ConstraintLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    val horizontalScrollViewId = View.generateViewId()
                    val dividerId = View.generateViewId()

                    val editor = Editor(ctx).apply {
                        editable = state.editable
                        if(isWordwrap.not()){
                            if (Settings.word_wrap_for_text){
                                isWordwrap = file.getName().endsWith(".txt")
                            }
                        }
                        id = View.generateViewId()
                        layoutParams = ConstraintLayout.LayoutParams(
                            ConstraintLayout.LayoutParams.MATCH_PARENT,
                            0
                        )

                        setThemeColors(
                            editorSurface = surfaceColor.toArgb(),
                            surfaceContainer = surfaceContainer.toArgb(),
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

                        state.editor = this

                        textmateScope?.let { langScope ->
                            scope.launch(Dispatchers.IO) {
                                val ext = file.getName().substringAfterLast(".").trim()

                                // Connect with debug language server
                                if (lsp_connections.contains(ext)) {
                                    baseLspConnector = BaseLspConnector(
                                        ext,
                                        textMateScope = textmateSources[ext]!!,
                                        port = lsp_connections[ext]!!
                                    )

                                    file.getParentFile()?.let { parent ->
                                        baseLspConnector?.connect(
                                            parent,
                                            fileObject = file,
                                            codeEditor = editorState.editor!!
                                        )
                                    }
                                    return@launch
                                }

                                val server = lspRegistry.find { it.supportedExtensions.map { e -> e.lowercase() }.contains(ext.lowercase()) }
                                if (server != null && Preference.getBoolean("lsp_${server.id}",true)) {
                                    lspConnection = ProcessConnection(server.command())

                                    // Connect with built-in language server
                                    if (server.isInstalled(context)) {
                                        baseLspConnector = BaseLspConnector(
                                            ext,
                                            textMateScope = textmateSources[ext]!!,
                                            connectionProvider = lspConnection!!
                                        )

                                        file.getParentFile()?.let { parent ->
                                            baseLspConnector?.connect(
                                                parent,
                                                fileObject = file,
                                                codeEditor = editorState.editor!!
                                            )
                                        }
                                        return@launch
                                    }

                                    dialog(
                                        context = context as Activity,
                                        title = strings.attention.getString(),
                                        msg = strings.ask_lsp_install.getFilledString(server.languageName),
                                        cancelString = strings.dont_ask_again,
                                        okString = strings.install,
                                        onOk = { server.install(context) },
                                        onCancel = {
                                            Preference.setBoolean(
                                                "lsp_${server.id}",
                                                false
                                            )
                                        }
                                    )
                                }

                                setLanguage(langScope)
                            }
                        }

                        val lspActions = createLspTextActions(scope, context, viewModel, file, editorState) { baseLspConnector }
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
                            if (!state.updateLock.isLocked){
                                state.isDirty = true
                                updateUndoRedo()
                                onTextChange.invoke()
                            }
                        }

                        subscribeAlways(EditorKeyEvent::class.java) { event ->
                            onKeyEvent.invoke(event)
                        }
                    }

                    val horizontalScrollView = HorizontalScrollView(ctx).apply {
                        state.arrowKeys = this
                        id = horizontalScrollViewId

                        visibility = if (Settings.show_arrow_keys){View.VISIBLE}else{ View.GONE}

                        layoutParams = ConstraintLayout.LayoutParams(
                            ConstraintLayout.LayoutParams.MATCH_PARENT,
                            ConstraintLayout.LayoutParams.WRAP_CONTENT
                        )
                        isHorizontalScrollBarEnabled = false
                        isSaveEnabled = false
                        addView(getInputView(editor,realSurface.toArgb(),onSurfaceColor.toArgb()))
                    }

                    val divider = View(ctx).apply {
                        id = dividerId
                        layoutParams = ConstraintLayout.LayoutParams(
                            ConstraintLayout.LayoutParams.MATCH_PARENT,
                            dpToPx(1f,ctx)
                        ).apply {
                            setBackgroundColor(divider.toArgb())
                        }
                    }

                    addView(editor)
                    addView(divider)
                    addView(horizontalScrollView)

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
                }
            },
        )
    }
}