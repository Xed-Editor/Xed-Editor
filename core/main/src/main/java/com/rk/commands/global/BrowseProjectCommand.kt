package com.rk.commands.global

import android.view.KeyEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.rk.DefaultScope
import com.rk.activities.main.ui.drawerStateRef
import com.rk.commands.ActionContext
import com.rk.commands.GlobalCommand
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.launch

class BrowseProjectCommand : GlobalCommand() {
    override val id: String = "global.browse_project"

    override fun getLabel(): String = strings.browse_project.getString()

    override fun getIcon(): Icon = Icon.VectorIcon(Icons.Rounded.Menu)

    override fun execute(context: ActionContext) {
        DefaultScope.launch(AndroidUiDispatcher.Main) {
            drawerStateRef.get()?.open()
        }
    }

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_B, ctrl = true)
}
