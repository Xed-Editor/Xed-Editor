package com.rk.tabs

import android.graphics.Color
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
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import com.rk.file.FileObject
import com.rk.libcommons.editor.BaseLspConnector
import com.rk.libcommons.editor.KarbonEditor
import com.rk.libcommons.editor.getInputView
import com.rk.libcommons.editor.textmateSources
import com.rk.libcommons.errorDialog
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.ui.components.EditorActions
import com.rk.xededitor.ui.components.SearchPanel
import com.rk.xededitor.ui.components.updateUndoRedo
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LINE_DIVIDER
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LINE_NUMBER_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.WHOLE_BACKGROUND
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

data class CodeEditorState(
    val initialContent: Content? = null,
) {
    var editor: KarbonEditor? = null
    var arrowKeys: HorizontalScrollView? = null
    var content by mutableStateOf(initialContent)
    var isDirty by mutableStateOf(false)
    var editable by mutableStateOf(Settings.readOnlyByDefault.not())
    val updateLock = Mutex()

    var isSearching by mutableStateOf(false)
    var isReplaceShown by mutableStateOf(false)
    var ignoreCase by mutableStateOf(true)
    var searchRegex by mutableStateOf(false)
    var searchWholeWord by mutableStateOf(false)
    var showOptionsMenu by mutableStateOf(false)
    var searchKeyword by mutableStateOf("")
    var replaceKeyword by mutableStateOf("")
}

var showControlPanel by mutableStateOf(false)

val lsp_connections = mutableMapOf<String, Int>()

@OptIn(DelicateCoroutinesApi::class)
class EditorTab(
    var file: FileObject,
    val viewModel: MainViewModel,
) : Tab() {

    private val charset = Charset.forName(Settings.encoding)
    private var baseLspConnector: BaseLspConnector? = null

    override val icon: ImageVector
        get() = Icons.Outlined.Edit

    override val name: String
        get() = strings.editor.getString()

    override var tabTitle: MutableState<String> = mutableStateOf(file.getName())


    val scope = CoroutineScope(Dispatchers.Default)
    val editorState by mutableStateOf(CodeEditorState())

    override fun onTabRemoved() {
        scope.cancel()
        editorState.content = null
        editorState.arrowKeys = null
        editorState.editor?.setText("")
        editorState.editor?.release()
        GlobalScope.launch{
            baseLspConnector?.disconnect()
        }
    }

    init {
        if (editorState.content == null){
            scope.launch(Dispatchers.IO){
                runCatching {
                    editorState.content = file.getInputStream().use {
                        ContentIO.createFrom(it, charset)
                    }
                    editorState.updateLock.withLock{
                        editorState.editor?.setText(editorState.content)
                    }

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
                file.writeText(editorState.content.toString(),charset)
                editorState.isDirty = false
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

            if (showControlPanel){
                ControlPanel(onDismissRequest = {
                    showControlPanel = false
                }, viewModel = viewModel)
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

            LaunchedEffect(Unit) {
                val ext = file.getName().substringAfterLast(".").toString().trim()
                if (lsp_connections.contains(ext)){
                    baseLspConnector = BaseLspConnector(ext,lsp_connections[ext]!!)
                    file.getParentFile()?.let { parent ->
                        baseLspConnector?.connect(parent, fileObject = file, karbonEditor = editorState.editor!!)
                    }
                }
            }


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

}


@OptIn(DelicateCoroutinesApi::class)
@Composable
fun CodeEditor(
    modifier: Modifier = Modifier,
    state: CodeEditorState,
    textmateScope: String? = null,
    onKeyEvent:(EditorKeyEvent)-> Unit,
    onTextChange:()-> Unit
) {

    val surfaceColor = if (isSystemInDarkTheme()){ MaterialTheme.colorScheme.surfaceDim }else{ MaterialTheme.colorScheme.surface }
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val selectionColors = LocalTextSelectionColors.current
    val realSurface = MaterialTheme.colorScheme.surface
    val selectionBackground = selectionColors.backgroundColor
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    var colorPrimary = MaterialTheme.colorScheme.primary
    val colorPrimaryContainer = MaterialTheme.colorScheme.primaryContainer
    val colorSecondary = MaterialTheme.colorScheme.secondary
    val handleColor = selectionColors.handleColor
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer



    AnimatedVisibility(visible = true) {
        val constraintSet = remember { ConstraintSet() }
        val scope = rememberCoroutineScope()

        AndroidView(
            modifier = modifier.fillMaxSize(),
            onRelease = {
                it.children.filterIsInstance<KarbonEditor>().firstOrNull()?.release()
            },
            update = {
                it.children.apply {
                    filterIsInstance<HorizontalScrollView>().firstOrNull()?.visibility = if (Settings.show_arrow_keys){View.VISIBLE}else{ View.GONE}

                    filterIsInstance<KarbonEditor>().firstOrNull()?.apply {
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
                            handleColor = handleColor.toArgb()
                        )
                    }
                }
            },
            factory = { ctx ->
                ConstraintLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )


                    val horizontalScrollViewId = View.generateViewId()

                    val editor = KarbonEditor(ctx).apply {
                        editable = state.editable
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
                            handleColor = handleColor.toArgb()
                        )

                        state.editor = this
                        textmateScope?.let { langScope ->
                            scope.launch(Dispatchers.IO) {
                                setLanguage(langScope)
                            }
                        }


                        scope.launch{
                            state.updateLock.withLock{
                                setText(state.content)
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

                    addView(editor)
                    addView(horizontalScrollView)

                    with(constraintSet) {
                        clone(this@apply)

                        connect(editor.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                        connect(editor.id, ConstraintSet.BOTTOM, horizontalScrollView.id, ConstraintSet.TOP)


                        connect(horizontalScrollView.id, ConstraintSet.TOP, editor.id, ConstraintSet.BOTTOM)
                        connect(horizontalScrollView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                        connect(editor.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                        connect(editor.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                        connect(horizontalScrollView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                        connect(horizontalScrollView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                        applyTo(this@apply)
                    }
                }
            },
        )
    }
}