package com.rk.tabs.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import java.lang.ref.WeakReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import org.ec4j.core.ResourceProperties

data class CodeEditorState(val initialContent: Content? = null) {
    var editor: WeakReference<Editor?> = WeakReference(null)

    var content = initialContent
    var isDirty by mutableStateOf(false)
    var editable by mutableStateOf(Settings.read_only_default.not())
    val updateLock = Mutex()

    var editorConfigLoaded: CompletableDeferred<ResourceProperties>? = null
    val contentLoaded = CompletableDeferred<Unit>()
    val contentRendered = CompletableDeferred<Unit>()

    val notices = mutableStateMapOf<String, @Composable (String) -> Unit>()

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

    var isWrapping by mutableStateOf(false)
    var isConnectingLsp by mutableStateOf(false)
}
