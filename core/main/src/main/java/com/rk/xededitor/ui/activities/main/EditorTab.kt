package com.rk.xededitor.ui.activities.main

import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import androidx.lifecycle.ViewModel
import com.rk.file.FileObject
import com.rk.libcommons.editor.KarbonEditor
import com.rk.libcommons.editor.getInputView
import com.rk.libcommons.editor.textmateSources
import com.rk.libcommons.errorDialog
import com.rk.libcommons.isMainThread
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.ui.components.EditorActions
import com.rk.xededitor.ui.components.SearchPanel
import com.rk.xededitor.ui.components.updateUndoRedo
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_ITEM_CURRENT
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LINE_DIVIDER
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LINE_NUMBER_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.MATCHED_TEXT_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SELECTED_TEXT_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme.WHOLE_BACKGROUND
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CodeEditorState(
    val initialContent: Content? = null
) {
    var editor: KarbonEditor? = null
    var content by mutableStateOf(initialContent)
    var isDirty by mutableStateOf(false)
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

@OptIn(DelicateCoroutinesApi::class)
class EditorTab(
    var file: FileObject,
    val viewModel: MainViewModel,
) : Tab() {

    override val icon: ImageVector
        get() = Icons.Outlined.Edit

    override val name: String
        get() = strings.editor.getString()

    override var tabTitle: MutableState<String> = mutableStateOf(file.getName())


    val scope = CoroutineScope(Dispatchers.Default)
    val editorState by mutableStateOf(CodeEditorState())

    override fun onTabRemoved() {
        editorState.editor?.release()
        MainActivity.instance?.editors?.apply {
            if (containsKey(this@EditorTab)){
                remove(this@EditorTab)
            }
        }
    }

    init {
        if (editorState.content == null){
            scope.launch(Dispatchers.IO){
                runCatching {
                    editorState.content = file.getInputStream().use {
                        ContentIO.createFrom(it)
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

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun save() = withContext(Dispatchers.IO){
        runCatching {
            file.writeText(editorState.content.toString())
            editorState.isDirty = false
        }.onFailure {
            errorDialog(it)
        }
    }

    override fun release() {
        scope.cancel()
        editorState.editor?.release()
        MainActivity.instance?.editors?.apply {
            if (containsKey(this@EditorTab)){
                remove(this@EditorTab)
            }
        }
    }

    override fun shouldOpenForFile(fileObject: FileObject): Boolean {
        return true
    }

    @Composable
    override fun content(){
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
                    root = MainActivity.instance?.editors[this@EditorTab],
                    setRoot = {
                        MainActivity.instance?.editors[this@EditorTab] = it
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
    override fun RowScope.actions(){
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
    root: ConstraintLayout? = null,
    setRoot:(ConstraintLayout)-> Unit,
    onKeyEvent:(EditorKeyEvent)-> Unit
) {
    AnimatedVisibility(visible = true) {
        val constraintSet = remember { ConstraintSet() }
        val scope = rememberCoroutineScope()

        val surfaceColor = if (isSystemInDarkTheme()){ MaterialTheme.colorScheme.surfaceDim }else{ MaterialTheme.colorScheme.surface }
        val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
        val realSurface = MaterialTheme.colorScheme.surface
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface

        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { ctx ->
                if (root == null){
                    ConstraintLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )


                        val horizontalScrollViewId = View.generateViewId()
                        val editor = KarbonEditor(ctx).apply {
                            id = View.generateViewId()
                            layoutParams = ConstraintLayout.LayoutParams(
                                ConstraintLayout.LayoutParams.MATCH_PARENT,
                                0
                            )
                            fun EditorColorScheme.setColors(color: Int, vararg keys: Int) {
                                keys.forEach { setColor(it, color) }
                            }

                            colorScheme.setColors(surfaceColor.toArgb(),WHOLE_BACKGROUND,LINE_NUMBER_BACKGROUND,LINE_DIVIDER)

                            state.editor = this
                            textmateScope?.let { langScope ->
                                scope.launch(Dispatchers.IO) {
                                    setLanguage(langScope)
                                }
                            }
                            updateColors { colors ->
                                with(colors){
                                    setColor(HIGHLIGHTED_DELIMITERS_UNDERLINE, Color.TRANSPARENT)

                                    setColors(
                                        onSurfaceColor.toArgb(),
                                        TEXT_ACTION_WINDOW_ICON_COLOR,
                                        COMPLETION_WND_TEXT_PRIMARY,
                                        COMPLETION_WND_TEXT_SECONDARY
                                    )

                                    setColors(
                                        surfaceColor.toArgb(),
                                        WHOLE_BACKGROUND,
                                        LINE_NUMBER_BACKGROUND
                                    )

                                    setColors(surfaceContainer.toArgb(),
                                        TEXT_ACTION_WINDOW_BACKGROUND,
                                        COMPLETION_WND_BACKGROUND)

                                    setColors(
                                        onSurfaceColor.toArgb(),
                                        EditorColorScheme.SELECTION_HANDLE,
                                        EditorColorScheme.SELECTION_INSERT,
                                        EditorColorScheme.BLOCK_LINE,
                                        EditorColorScheme.BLOCK_LINE_CURRENT,
                                        HIGHLIGHTED_DELIMITERS_FOREGROUND
                                    )

//                            setColors(
//                                transparentPrimary,
//                                SELECTED_TEXT_BACKGROUND,
//                                COMPLETION_WND_ITEM_CURRENT
//                            )

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
                                }
                            }

                            subscribeAlways(EditorKeyEvent::class.java) { event ->
                                onKeyEvent.invoke(event)
                            }
                        }

                        val horizontalScrollView = HorizontalScrollView(ctx).apply {
                            id = horizontalScrollViewId
                            visibility = View.VISIBLE
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
                        setRoot(this)
                    }
                }else{
                    root
                }
            },
        )
    }
}