package com.rk.xededitor.ui.components

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor

data class CodeEditorState(
    var editor: CodeEditor? = null,
    val initialContent: Content = Content()
) {
    var content by mutableStateOf(initialContent)
}

@Composable
fun rememberCodeEditorState(
    initialContent: Content = Content()
) = remember {
    CodeEditorState(
        initialContent = initialContent
    )
}

@Composable
fun CodeEditor(
    modifier: Modifier = Modifier,
    state: CodeEditorState,
) {
    val context = LocalContext.current
    val editor = remember {
        setCodeEditorFactory(
            context = context,
            state = state
        )
    }
    AndroidView(
        factory = { editor },
        modifier = modifier,
        onRelease = {
            it.release()
        }
    )

    LaunchedEffect(key1 = state.content) {
        state.editor?.setText(state.content)
    }

}

private fun setCodeEditorFactory(
    context: Context,
    state: CodeEditorState
): CodeEditor {
    val editor = CodeEditor(context)
    editor.apply {
        setText(state.content)

    }
    state.editor = editor
    return editor
}


class MainViewModel : ViewModel() {
    val editorState by mutableStateOf(
        CodeEditorState()
    )
}

@Composable
fun TabContent(modifier: Modifier = Modifier, file: com.rk.file_wrapper.FileObject, type: FragmentType, viewModel: MainViewModel = viewModel()) {
    when(type){
        FragmentType.EDITOR -> {
            CodeEditor(
                modifier = Modifier
                    .fillMaxSize(),
                state = viewModel.editorState
            )
        }
        else -> {
            Text(text = "Unsupported")
        }
    }
}