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

class ReplaceCommand : EditorCommand() {
    override val id: String = "editor.replace"

    override fun getLabel(): String = strings.replace.getString()

    override fun action(editorActionContext: EditorActionContext) {
        editorActionContext.editorTab.editorState.apply {
            editorActionContext.editor.getSelectedText()?.let {
                searchKeyword = searchKeyword.copy(text = it, selection = TextRange(it.length))
            }
            isSearching = true
            isReplaceShown = true
        }
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.find_replace)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_H, ctrl = true)
}
