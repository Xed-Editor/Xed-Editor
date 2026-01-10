package com.rk.commands.global

import android.content.Intent
import android.view.KeyEvent
import androidx.core.net.toUri
import com.rk.commands.ActionContext
import com.rk.commands.CommandContext
import com.rk.commands.GlobalCommand
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.icons.Menu_book
import com.rk.icons.XedIcons
import com.rk.resources.getString
import com.rk.resources.strings

class DocumentationCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.documentation"

    override fun getLabel(): String = strings.docs.getString()

    override fun action(actionContext: ActionContext) {
        val url = "https://xed-editor.github.io/Xed-Docs/"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        commandContext.mainActivity.startActivity(intent)
    }

    override fun getIcon(): Icon = Icon.VectorIcon(XedIcons.Menu_book)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_F1)
}
