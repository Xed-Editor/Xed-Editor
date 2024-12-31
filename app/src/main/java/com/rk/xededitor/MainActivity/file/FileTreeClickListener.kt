package com.rk.xededitor.MainActivity.file

import com.rk.filetree.interfaces.FileClickListener
import com.rk.filetree.interfaces.FileLongClickListener
import com.rk.filetree.interfaces.FileObject
import com.rk.filetree.model.Node
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.application
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity.Companion.activityRef
import com.rk.xededitor.MainActivity.file.ProjectManager.getSelectedProjectRootFile
import com.rk.xededitor.MainActivity.handlers.MenuItemHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

val fileClickListener =
    object : FileClickListener {
        override fun onClick(node: Node<FileObject>) {
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
                    !PreferencesData.getBoolean(
                        PreferencesKeys.KEEP_DRAWER_LOCKED,
                        false,
                    )
                ) {
                    it.binding!!.drawerLayout.close()
                }
                
                DefaultScope.launch(Dispatchers.Main) {
                    delay(2000)
                    MenuItemHandler.update(it)
                }
            }
        }
    }

val fileLongClickListener =
    object : FileLongClickListener {
        override fun onLongClick(node: Node<FileObject>) {
            activityRef.get()?.apply {
                getSelectedProjectRootFile(this)?.let {
                    FileAction(this, File(it.getAbsolutePath()), File(node.value.getAbsolutePath()))
                }
            }
        }
    }
