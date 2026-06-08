package com.rk.commands.editor

import android.view.KeyEvent
import androidx.compose.ui.text.TextRange
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class SearchCommand : EditorCommand() {
    override val id: String = "editor.search"

    override fun getLabel(): String = strings.search.getString()

    override fun action(editorActionContext: EditorActionContext) {
        editorActionContext.editorTab.editorState.apply {
            editorActionContext.editor.getSelectedText()?.let {
                searchKeyword = searchKeyword.copy(text = it, selection = TextRange(it.length))
            }
            isSearching = true
            isReplaceShown = false
        }
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.search)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_F, ctrl = true)
}
