package com.rk.extension

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.rk.file.FileObject
import com.rk.xededitor.ui.activities.main.ControlItem
import com.rk.tabs.Tab

data class CustomTab(val shouldOpenForFile:(fileObject: FileObject)->Boolean,val tab: Tab)

object Hooks {

    //root.(file)
    data class FileAction(
        val id: String,
        val shouldAttach: FileObject?.(FileObject) -> Boolean,
        val icon: ImageVector,
        val title: String,
        val onClick: FileObject?.(FileObject) -> Unit
    ) {
        companion object {
            val actions = mutableStateMapOf<String, FileAction>()
        }
    }

    object Editor {
        val tabs = mutableStateMapOf<String, CustomTab>()
    }

    object ControlItems{
        val items = mutableStateMapOf<String, ControlItem>()
    }

    object Settings{
        val screens = mutableStateMapOf<String, SettingsScreen>()
    }
}