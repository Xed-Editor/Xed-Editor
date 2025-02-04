package com.rk.xededitor.MainActivity.file

import com.rk.filetree.interfaces.FileClickListener
import com.rk.filetree.interfaces.FileLongClickListener
import com.rk.filetree.model.Node
import com.rk.libcommons.DefaultScope
import com.rk.settings.Settings
import com.rk.settings.SettingsKey
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.MainActivity.Companion.activityRef
import com.rk.xededitor.MainActivity.file.ProjectManager.getSelectedProjectRootFile
import com.rk.xededitor.MainActivity.handlers.updateMenu
import kotlinx.coroutines.launch

val fileClickListener =
    object : FileClickListener {
        override fun onClick(node: Node<com.rk.file.FileObject>) {
            if (node.value.isDirectory()) {
                return
            }
            
            activityRef.get()?.let {
                if (it.isPaused) {
                    return@let
                }
                val file = node.value

                it.adapter!!.addFragment(file)
                if (
                    !Settings.getBoolean(
                        SettingsKey.KEEP_DRAWER_LOCKED,
                        false,
                    )
                ) {
                    it.binding!!.drawerLayout.close()
                }

                DefaultScope.launch { updateMenu(MainActivity.activityRef.get()?.adapter?.getCurrentFragment()) }
            }
        }
    }

val fileLongClickListener =
    object : FileLongClickListener {
        override fun onLongClick(node: Node<com.rk.file.FileObject>) {
            activityRef.get()?.apply {
                getSelectedProjectRootFile(this)?.let {
                    FileAction(this, it, node.value)
                }
            }
        }
    }
