package com.rk.xededitor.MainActivity

import android.annotation.SuppressLint
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rk.file.FileWrapper
import com.rk.libcommons.ActionPopup
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.rk.xededitor.R
import kotlinx.coroutines.launch

object ProjectBar {
    @SuppressLint("SetTextI18n")
    fun setupNavigationRail(activity: MainActivity) {
        with(activity) {
            val openFileId = View.generateViewId()
            val openDirId = View.generateViewId()
            val openPathId = View.generateViewId()
            val privateFilesId = View.generateViewId()

            var dialog: AlertDialog? = null

            val listener =
                View.OnClickListener { v ->
                    when (v.id) {
                        openFileId -> {
                            fileManager?.requestOpenFile()
                        }

                        openDirId -> {
                            fileManager?.requestOpenDirectory()
                        }

                        openPathId -> {
                            fileManager?.requestOpenFromPath()
                        }

                        privateFilesId -> {
                            lifecycleScope.launch {
                                ProjectManager.addProject(this@with,
                                    FileWrapper(filesDir.parentFile!!)
                                )
                            }
                        }
                    }
                    dialog?.dismiss()
                    dialog = null
                }

            fun handleAddNew() {
                ActionPopup(this).apply {
                    addItem(
                        getString(strings.open_directory),
                        getString(strings.open_dir_desc),
                        ContextCompat.getDrawable(this@with, drawables.outline_folder_24),
                        openDirId,
                        listener,
                    )
                    addItem(
                        getString(strings.open_path),
                        getString(strings.open_path_desc),
                        ContextCompat.getDrawable(this@with, drawables.android),
                        openPathId,
                        listener,
                    )
                    
                    addItem(
                        getString(strings.private_files),
                        getString(strings.private_files_desc),
                        ContextCompat.getDrawable(this@with, drawables.android),
                        privateFilesId,
                        listener,
                    )

                    setTitle(getString(strings.add))
                    getDialogBuilder().setNegativeButton(getString(strings.cancel), null)
                    dialog = show()
                }
            }

            binding!!.navigationRail.setOnItemSelectedListener { item ->
                if (item.itemId == R.id.add_new) {
                    handleAddNew()
                    false
                } else {
                    ProjectManager.projects[item.itemId]?.let {
                        ProjectManager.changeProject(it, this)
                    }
                    true
                }
            }
        }
    }
}
