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
import com.rk.xededitor.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.resources.strings
import com.rk.resources.drawable
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
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
            val sftp = View.generateViewId()
            
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
                            activity.projectManager.addProject(this, filesDir.parentFile!!)
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
                                    hint = getString(strings.git_branch)
                                    setText("main")
                                }
                            MaterialAlertDialogBuilder(this)
                                .setTitle(getString(strings.clone_repo))
                                .setView(view)
                                .setNegativeButton(getString(strings.cancel), null)
                                .setPositiveButton(getString(strings.apply)) { _, _ ->
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
                                        rkUtils.toast(getString(strings.fill_both))
                                    } else if (repoDir.exists()) {
                                        rkUtils.toast("$repoDir ${getString(strings.exists)}")
                                    } else {
                                        val loadingPopup =
                                            LoadingPopup(this, null)
                                                .setMessage(getString(strings.cloning))
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
                                                    activity.projectManager.addProject(this@with, repoDir)
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
                                                        rkUtils.toast(getString(strings.clone_err))
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
                                                            activity.projectManager.addProject(
                                                                this@with,
                                                                repoDir,
                                                            )
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            loadingPopup.hide()
                                                            rkUtils.toast(
                                                                "${getString(strings.err)}: ${e.message}"
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
                        
                        sftp -> {
                            val view = LayoutInflater.from(this@with).inflate(R.layout.popup_new, null)
                            val editText = view.findViewById<View>(R.id.name) as EditText
                            editText.hint = "user:password@host:port/path"
                            MaterialAlertDialogBuilder(this)
                                .setTitle("Add SFTP folder")
                                .setView(view)
                                .setNegativeButton(getString(R.string.cancel), null)
                                .setPositiveButton(getString(R.string.apply)) { _, _ ->
                                    val text = editText.text.toString()
                                    if (text.isEmpty()) {
                                        return@setPositiveButton
                                    }
                                    activity.projectManager.addRemoteProject(this, text)
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
                        getString(strings.open_directory),
                        getString(strings.open_dir_desc),
                        ContextCompat.getDrawable(this@with, drawable.outline_folder_24),
                        openDirId,
                        listener,
                    )
                    addItem(
                        getString(strings.open_path),
                        getString(strings.open_path_desc),
                        ContextCompat.getDrawable(this@with, drawable.android),
                        openPathId,
                        listener,
                    )
                    addItem(
                        getString(strings.private_files),
                        getString(strings.private_files_desc),
                        ContextCompat.getDrawable(this@with, drawable.android),
                        privateFilesId,
                        listener,
                    )
                    addItem(
                        getString(strings.clone_repo),
                        getString(strings.clone_repo_desc),
                        ContextCompat.getDrawable(this@with, drawable.git),
                        cloneRepo,
                        listener,
                    )
                    addItem(
                        "SFTP",
                        "Open remote folder via SFTP",
                        ContextCompat.getDrawable(this@with, R.drawable.dns),
                        sftp,
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
                    activity.projectManager.changeProject(item.itemId,activity)
                    true
                }
            }
        }
    }
}