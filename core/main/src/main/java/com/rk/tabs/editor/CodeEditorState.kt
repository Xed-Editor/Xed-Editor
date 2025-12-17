package com.rk.tabs.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.constraintlayout.widget.ConstraintLayout
import com.rk.components.CodeItem
import com.rk.editor.Editor
import com.rk.runner.RunnerImpl
import com.rk.settings.Settings
import io.github.rosemoe.sora.text.Content
import java.lang.ref.WeakReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex

data class CodeEditorState(val initialContent: Content? = null) {
    var editor: WeakReference<Editor?> = WeakReference(null)
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
    var isConnectingLsp by mutableStateOf(false)
}
