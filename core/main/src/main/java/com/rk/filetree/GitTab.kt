package com.rk.filetree

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rk.icons.Icon
import com.rk.resources.drawables

class GitTab() : DrawerTab() {
    @Composable
    override fun Content(modifier: Modifier) {
        // todo
    }

    override fun getName(): String {
        return "Git"
    }

    override fun getIcon(): Icon {
        return Icon.DrawableRes(drawables.git)
    }
}