package com.rk.commands.global

import android.view.KeyEvent
import com.rk.commands.ActionContext
import com.rk.commands.GlobalCommand
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.icons.Menu_book
import com.rk.icons.XedIcons
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.openUrl

class DocumentationCommand : GlobalCommand() {
    override val id: String = "global.documentation"

    override fun getLabel(): String = strings.docs.getString()

    override fun action(actionContext: ActionContext) {
        val url = "https://xed-editor.github.io/Xed-Docs/"
        actionContext.currentActivity.openUrl(url)
    }

    override fun getIcon(): Icon = Icon.VectorIcon(XedIcons.Menu_book)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_F1)
}
