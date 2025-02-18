package com.rk.xededitor.MainActivity

import android.annotation.SuppressLint
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.file_wrapper.FileWrapper
import com.rk.libcommons.ActionPopup
import com.rk.libcommons.alpineHomeDir
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.BuildConfig
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

                    if (BuildConfig.DEBUG){
                        addItem(
                            getString(strings.private_files),
                            getString(strings.private_files_desc),
                            ContextCompat.getDrawable(this@with, drawables.build),
                            listener = {
                                if (Settings.has_shown_private_data_dir_warning.not()){
                                    MaterialAlertDialogBuilder(this@with).apply {
                                        setCancelable(false)
                                        setTitle(strings.warning)
                                        setMessage(strings.warning_private_dir)
                                        setPositiveButton(strings.ok){ _,_ ->
                                            Settings.has_shown_private_data_dir_warning = true
                                            lifecycleScope.launch {
                                                ProjectManager.addProject(this@with,
                                                    FileWrapper(filesDir.parentFile!!)
                                                )
                                            }
                                        }
                                        show()
                                    }
                                }else{
                                    lifecycleScope.launch {
                                        ProjectManager.addProject(this@with,
                                            FileWrapper(filesDir.parentFile!!)
                                        )
                                    }
                                }

                            }
                        )
                    }


                    addItem(
                        strings.terminal_home.getString(),
                        strings.terminal_home_desc.getString(),
                        ContextCompat.getDrawable(this@with, drawables.terminal),
                        listener = {
                            if (Settings.has_shown_terminal_dir_warning.not()){
                                MaterialAlertDialogBuilder(this@with).apply {
                                    setCancelable(false)
                                    setTitle(strings.warning)
                                    setMessage(strings.warning_private_dir)
                                    setPositiveButton(strings.ok){ _,_ ->
                                        Settings.has_shown_terminal_dir_warning = true
                                        lifecycleScope.launch {
                                            ProjectManager.addProject(this@with,
                                                FileWrapper(alpineHomeDir())
                                            )
                                        }
                                    }
                                    show()
                                }

                            }else{
                                lifecycleScope.launch {
                                    ProjectManager.addProject(this@with,
                                        FileWrapper(alpineHomeDir())
                                    )
                                }
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
