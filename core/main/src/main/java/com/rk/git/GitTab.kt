package com.rk.git

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rk.activities.main.gitViewModel
import com.rk.filetree.DrawerTab
import com.rk.icons.Icon
import com.rk.resources.drawables

import androidx.compose.material3.Text

class GitTab() : DrawerTab() {
    @Composable
    override fun Content(modifier: Modifier) {
        Text(gitViewModel.get()!!.getCurrentRoot())
    }

    override fun getName(): String {
        return "Git"
    }

    override fun getIcon(): Icon {
        return Icon.DrawableRes(drawables.git)
    }
}