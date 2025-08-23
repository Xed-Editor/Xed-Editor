package com.rk.extension

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.rk.file.FileObject
import com.rk.xededitor.ui.activities.main.ControlItem
import com.rk.xededitor.ui.activities.main.Tab

object Hooks {
    data class FileAction(
        val id: String,
        val shouldAttach: FileObject.(FileObject) -> Unit,
        val icon: ImageVector,
        val title: String,
        val description: String? = null,
        val onClick: FileObject.(FileObject) -> Unit
    ) {
        companion object {
            val actions = mutableStateMapOf<String, FileAction>()
        }
    }

    object Editor {
        val tabs = mutableStateMapOf<String, Tab>()
    }

    object ControlItems{
        val items = mutableStateMapOf<String, ControlItem>()
    }

    object Settings{
        val screens = mutableStateMapOf<String, SettingsScreen>()
    }
}