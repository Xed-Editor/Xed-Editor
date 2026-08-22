package com.rk.tabs.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import com.rk.color.ColorFormat
import com.rk.editor.Editor
import com.rk.search.CodeItem
import com.rk.settings.Settings
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.TextRange
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import org.ec4j.core.ResourceProperties
import java.lang.ref.WeakReference

data class CodeEditorState(val initialContent: Content? = null) {
    var editor: WeakReference<Editor?> = WeakReference(null)

    var content = initialContent
    var isDirty by mutableStateOf(false)
    var editable by mutableStateOf(Settings.read_only_default.not())
    val updateLock = Mutex()

    var editorConfigLoaded: CompletableDeferred<ResourceProperties>? = null
    var formatDeferred: CompletableDeferred<Boolean>? = null
    val contentLoaded = CompletableDeferred<Unit>()
    val contentRendered = CompletableDeferred<Unit>()

    private val _notices = MutableStateFlow<Map<String, @Composable (String) -> Unit>>(emptyMap())
    val notices: StateFlow<Map<String, @Composable (String) -> Unit>> = _notices.asStateFlow()

    fun addNotice(id: String, notice: @Composable (String) -> Unit) {
        _notices.update { it + (id to notice) }
    }

    fun removeNotice(id: String) {
        _notices.update { it - id }
    }

    var isSearching by mutableStateOf(false)
    var isReplaceShown by mutableStateOf(false)
    var ignoreCase by mutableStateOf(true)
    var searchRegex by mutableStateOf(false)
    var searchWholeWord by mutableStateOf(false)
    var showOptionsMenu by mutableStateOf(false)
    var searchKeyword by mutableStateOf(TextFieldValue(""))
    var replaceKeyword by mutableStateOf(TextFieldValue(""))

    var showFindingsDialog by mutableStateOf(false)
    var findingsItems by mutableStateOf(listOf<CodeItem>())
    var findingsTitle by mutableStateOf("")
    var findingsDescription by mutableStateOf("")

    var showJumpToLineDialog by mutableStateOf(false)
    var jumpToLineValue by mutableStateOf("")
    var jumpToLineError by mutableStateOf<String?>(null)

    var showRenameDialog by mutableStateOf(false)
    var renameValue by mutableStateOf("")
    var renameError by mutableStateOf<String?>(null)
    var renameConfirm by mutableStateOf<((String) -> Unit)?>(null)

    var textmateScope by mutableStateOf<String?>(null)

    var showColorPicker by mutableStateOf<Pair<Color, ColorFormat>?>(null)
    var colorPickerRange by mutableStateOf<TextRange?>(null)

    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    fun updateUndoRedo() {
        canUndo = editor.get()?.canUndo() ?: false
        canRedo = editor.get()?.canRedo() ?: false
    }
}
