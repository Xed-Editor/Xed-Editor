package com.rk.xededitor.MainActivity.file

import com.rk.filetree.interfaces.FileClickListener
import com.rk.filetree.interfaces.FileLongClickListener
import com.rk.filetree.interfaces.FileObject
import com.rk.filetree.model.Node
import com.rk.libcommons.DefaultScope
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity.Companion.activityRef
import com.rk.xededitor.MainActivity.file.ProjectManager.getSelectedProjectRootFilePath
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
                val file = File(node.value.getAbsolutePath())
                
                it.adapter.addFragment(file)
                if (
                    !PreferencesData.getBoolean(
                        PreferencesKeys.KEEP_DRAWER_LOCKED,
                        false,
                    )
                ) {
                    it.binding.drawerLayout.close()
                }
                
                DefaultScope.launch(Dispatchers.Main) {
                    //delay(1000)
                    MenuItemHandler.update(it)
                }
            }
        }
    }

val fileLongClickListener =
    object : FileLongClickListener {
        override fun onLongClick(node: Node<FileObject>) {
            activityRef.get()?.apply {
                getSelectedProjectRootFilePath(this)?.let {
                    FileAction(this, File(it), File(node.value.getAbsolutePath()))
                }
            }
        }
    }