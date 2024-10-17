package com.rk.xededitor.MainActivity

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.ActionPopup
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.plugin.server.PluginUtils.getPluginRoot
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

object ProjectBar {
    @SuppressLint("SetTextI18n")
    fun setupNavigationRail(activity: MainActivity) {
        with(activity) {
            val openFileId = View.generateViewId()
            val openDirId = View.generateViewId()
            val openPathId = View.generateViewId()
            val privateFilesId = View.generateViewId()
            val cloneRepo = View.generateViewId()
            val pluginDir = View.generateViewId()

            var dialog: AlertDialog? = null

            val listener =
                View.OnClickListener { v ->
                    when (v.id) {
                        openFileId -> {
                            fileManager.requestOpenFile()
                        }

                        openDirId -> {
                            fileManager.requestOpenDirectory()
                        }

                        openPathId -> {
                            fileManager.requestOpenFromPath()
                        }

                        privateFilesId -> {
                            ProjectManager.addProject(this, filesDir.parentFile!!)
                        }

                        pluginDir -> {
                            ProjectManager.addProject(
                                this,
                                getPluginRoot().also {
                                    if (it.exists().not()) {
                                        it.mkdirs()
                                    }
                                },
                            )
                        }

                        cloneRepo -> {
                            val view =
                                LayoutInflater.from(this@with).inflate(R.layout.popup_new, null)
                            view.findViewById<LinearLayout>(R.id.mimeTypeEditor).visibility =
                                View.VISIBLE
                            val repoLinkEdit =
                                view.findViewById<EditText>(R.id.name).apply {
                                    hint = "https://github.com/UserName/repo.git"
                                }
                            val branchEdit =
                                view.findViewById<EditText>(R.id.mime).apply {
                                    hint = getString(R.string.git_branch)
                                    setText("main")
                                }
                            MaterialAlertDialogBuilder(this)
                                .setTitle(getString(R.string.clone_repo))
                                .setView(view)
                                .setNegativeButton(getString(R.string.cancel), null)
                                .setPositiveButton(getString(R.string.apply)) { _, _ ->
                                    val repoLink = repoLinkEdit.text.toString()
                                    val branch = branchEdit.text.toString()
                                    val repoName =
                                        repoLink.substringAfterLast("/").removeSuffix(".git")
                                    val repoDir =
                                        File(
                                            PreferencesData.getString(
                                                PreferencesKeys.GIT_REPO_DIR,
                                                "/storage/emulated/0",
                                            ) + "/" + repoName
                                        )
                                    if (repoLink.isEmpty() || branch.isEmpty()) {
                                        rkUtils.toast(getString(R.string.fill_both))
                                    } else if (repoDir.exists()) {
                                        rkUtils.toast("$repoDir ${getString(R.string.exists)}")
                                    } else {
                                        val loadingPopup =
                                            LoadingPopup(this, null)
                                                .setMessage(getString(R.string.cloning))
                                        loadingPopup.show()
                                        DefaultScope.launch(Dispatchers.IO) {
                                            try {
                                                Git.cloneRepository()
                                                    .setURI(repoLink)
                                                    .setDirectory(repoDir)
                                                    .setBranch(branch)
                                                    .call()
                                                withContext(Dispatchers.Main) {
                                                    loadingPopup.hide()
                                                    ProjectManager.addProject(this@with, repoDir)
                                                }
                                            } catch (e: Exception) {
                                                val credentials =
                                                    PreferencesData.getString(
                                                            PreferencesKeys.GIT_CRED,
                                                            "",
                                                        )
                                                        .split(":")
                                                if (credentials.size != 2) {
                                                    withContext(Dispatchers.Main) {
                                                        loadingPopup.hide()
                                                        rkUtils.toast(getString(R.string.clone_err))
                                                    }
                                                } else {
                                                    try {
                                                        Git.cloneRepository()
                                                            .setURI(repoLink)
                                                            .setDirectory(repoDir)
                                                            .setBranch(branch)
                                                            .setCredentialsProvider(
                                                                UsernamePasswordCredentialsProvider(
                                                                    credentials[0],
                                                                    credentials[1],
                                                                )
                                                            )
                                                            .call()
                                                        withContext(Dispatchers.Main) {
                                                            loadingPopup.hide()
                                                            ProjectManager.addProject(
                                                                this@with,
                                                                repoDir,
                                                            )
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            loadingPopup.hide()
                                                            rkUtils.toast(
                                                                "${getString(R.string.err)}: ${e.message}"
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                .show()
                        }
                    }
                    dialog?.dismiss()
                    dialog = null
                }

            fun handleAddNew() {
                ActionPopup(this).apply {
                    addItem(
                        getString(R.string.open_directory),
                        getString(R.string.open_dir_desc),
                        ContextCompat.getDrawable(this@with, R.drawable.outline_folder_24),
                        openDirId,
                        listener,
                    )
                    addItem(
                        getString(R.string.open_file),
                        getString(R.string.open_file_desc),
                        ContextCompat.getDrawable(
                            this@with,
                            R.drawable.outline_insert_drive_file_24,
                        ),
                        openFileId,
                        listener,
                    )
                    addItem(
                        getString(R.string.open_path),
                        getString(R.string.open_path_desc),
                        ContextCompat.getDrawable(this@with, R.drawable.android),
                        openPathId,
                        listener,
                    )
                    addItem(
                        getString(R.string.clone_repo),
                        getString(R.string.clone_repo_desc),
                        ContextCompat.getDrawable(this@with, R.drawable.git),
                        cloneRepo,
                        listener,
                    )
                    addItem(
                        getString(R.string.plugin),
                        getString(R.string.plugin_dir),
                        ContextCompat.getDrawable(this@with, R.drawable.extension),
                        pluginDir,
                        listener,
                    )
                    addItem(
                        getString(R.string.private_files),
                        getString(R.string.private_files_desc),
                        ContextCompat.getDrawable(this@with, R.drawable.android),
                        privateFilesId,
                        listener,
                    )

                    setTitle(getString(R.string.add))
                    getDialogBuilder().setNegativeButton(getString(R.string.cancel), null)
                    dialog = show()
                }
            }

            binding.navigationRail.setOnItemSelectedListener { item ->
                if (item.itemId == R.id.add_new) {
                    handleAddNew()
                    false
                } else {
                    ProjectManager.projects[item.itemId]?.let {
                        ProjectManager.changeProject(File(it), this)
                    }
                    true
                }
            }

            // close drawer if same item is selected again except add_new item
            binding.navigationRail.setOnItemReselectedListener { item ->
                if (item.itemId == R.id.add_new) {
                    handleAddNew()
                }
            }
        }
    }
}
