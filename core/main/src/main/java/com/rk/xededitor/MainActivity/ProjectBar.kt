package com.rk.xededitor.MainActivity

import android.annotation.SuppressLint
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rk.file_wrapper.FileWrapper
import com.rk.libcommons.ActionPopup
import com.rk.libcommons.alpineHomeDir
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.rk.xededitor.R
import kotlinx.coroutines.launch

object ProjectBar {
    @SuppressLint("SetTextI18n")
    fun setupNavigationRail(activity: MainActivity) {
        with(activity) {
            fun handleAddNew() {
                ActionPopup(this,true).apply {
                    addItem(
                        getString(strings.open_directory),
                        getString(strings.open_dir_desc),
                        ContextCompat.getDrawable(this@with, drawables.outline_folder_24),
                        listener = {
                            fileManager?.requestOpenDirectory()
                        }
                    )
                    addItem(
                        getString(strings.open_path),
                        getString(strings.open_path_desc),
                        ContextCompat.getDrawable(this@with, drawables.android),
                        listener = {
                            fileManager?.requestOpenFromPath()
                        }
                    )
                    
                    addItem(
                        getString(strings.private_files),
                        getString(strings.private_files_desc),
                        ContextCompat.getDrawable(this@with, drawables.build),
                        listener = {
                            lifecycleScope.launch {
                                ProjectManager.addProject(this@with,
                                    FileWrapper(filesDir.parentFile!!)
                                )
                            }
                        }
                    )

                    addItem(
                        strings.terminal_home.getString(),
                        strings.terminal_home_desc.getString(),
                        ContextCompat.getDrawable(this@with, drawables.terminal),
                        listener = {
                            lifecycleScope.launch {
                                ProjectManager.addProject(this@with,
                                    FileWrapper(alpineHomeDir())
                                )
                            }
                        }
                    )


                    setTitle(getString(strings.add))
                    getDialogBuilder().setNegativeButton(getString(strings.cancel), null)
                    show()
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
