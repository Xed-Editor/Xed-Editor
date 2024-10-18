package com.rk.xededitor.MainActivity.handlers

import android.content.Intent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.ActionPopup
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.Printer
import com.rk.runner.Runner
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.BatchReplacement
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.editor.fragments.core.FragmentType
import com.rk.xededitor.MainActivity.editor.fragments.editor.EditorFragment
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.getString
import com.rk.xededitor.terminal.Terminal
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

typealias Id = R.id

object MenuClickHandler {
    
    private var searchText: String? = ""
    
    @OptIn(DelicateCoroutinesApi::class)
    fun handle(activity: MainActivity, menuItem: MenuItem): Boolean {
        val id = menuItem.itemId
        val editorFragment = if (activity.adapter.getCurrentFragment()?.fragment is EditorFragment) {
            activity.adapter.getCurrentFragment()?.fragment as EditorFragment
        } else {
            null
        }
        
        when (id) {
            Id.run -> {
                editorFragment?.file?.let { it1 -> Runner.run(it1, activity) }
                return true
            }
            
            Id.action_all -> {
                activity.adapter.tabFragments.values.forEach { f ->
                    if (f.get()?.type == FragmentType.EDITOR) {
                        (f.get()?.fragment as EditorFragment).save(false)
                    }
                }
                rkUtils.toast("Saved all files")
                return true
            }
            
            Id.action_save -> {
                editorFragment?.save(true)
                return true
            }
            
            Id.undo -> {
                editorFragment?.undo()
                return true
            }
            
            Id.redo -> {
                editorFragment?.redo()
                return true
            }
            
            Id.action_settings -> {
                activity.startActivity(Intent(activity, SettingsActivity::class.java))
                return true
            }
            
            Id.terminal -> {
                // Handle terminal
                activity.startActivity(Intent(activity, Terminal::class.java))
                return true
            }
            
            Id.action_print -> {
                Printer.print(
                    activity,
                    editorFragment?.editor?.text.toString(),
                )
                return true
            }
            
            Id.batchrep -> {
                activity.startActivity(Intent(activity, BatchReplacement::class.java))
                return true
            }
            
            Id.search -> {
                // Handle search
                handleSearch(activity)
                return true
            }
            
            Id.search_next -> {
                editorFragment?.editor?.searcher?.gotoNext()
                return true
            }
            
            Id.search_previous -> {
                editorFragment?.editor?.searcher?.gotoPrevious()
                return true
            }
            
            Id.search_close -> {
                // Handle search_close
                handleSearchClose(activity)
                return true
            }
            
            Id.replace -> {
                // Handle replace
                handleReplace(activity)
                return true
            }
            
            Id.share -> {
                rkUtils.shareText(
                    activity,
                    editorFragment?.editor?.text.toString(),
                )
                return true
            }
            
            Id.suggestions -> {
                editorFragment?.editor?.showSuggestions(editorFragment.editor?.isShowSuggestion()?.not() == true)
                return true
            }
            
            Id.git -> {
                val pull = View.generateViewId()
                val push = View.generateViewId()
                
                var dialog: AlertDialog? = null
                val credentials = PreferencesData.getString(PreferencesKeys.GIT_CRED, "").split(":")
                if (credentials.size != 2) {
                    rkUtils.toast(getString(R.string.inavalid_git_cred))
                    return true
                }
                val userdata = PreferencesData.getString(PreferencesKeys.GIT_USER_DATA, "").split(":")
                if (userdata.size != 2) {
                    rkUtils.toast(getString(R.string.inavalid_userdata))
                    return true
                }
                val listener = View.OnClickListener { v ->
                    when (v.id) {
                        pull -> {
                            val loadingPopup = LoadingPopup(activity, null).setMessage(getString(R.string.wait_download))
                            loadingPopup.show()
                            
                            DefaultScope.launch(Dispatchers.IO) {
                                try {
                                    val gitRoot = FileManager.findGitRoot(
                                        editorFragment?.file
                                    )
                                    if (gitRoot != null) {
                                        val git = Git.open(gitRoot)
                                        git.pull().setCredentialsProvider(
                                            UsernamePasswordCredentialsProvider(
                                                credentials[0],
                                                credentials[1],
                                            )
                                        ).call()
                                    }
                                } catch (e: GitAPIException) {
                                    rkUtils.toast(e.message)
                                }
                                withContext(Dispatchers.Main) {
                                    rkUtils.toast(getString(R.string.done))
                                    loadingPopup.hide()
                                }
                            }
                        }
                        
                        push -> {
                            val gitRoot = FileManager.findGitRoot(
                                editorFragment?.file
                            )
                            if (gitRoot != null) {
                                val git = Git.open(gitRoot)
                                val view = LayoutInflater.from(activity).inflate(R.layout.popup_new, null)
                                view.findViewById<LinearLayout>(Id.mimeTypeEditor).visibility = View.VISIBLE
                                val branchedit = view.findViewById<EditText>(Id.name).apply {
                                    hint = getString(R.string.git_branch)
                                    setText(git.repository.branch)
                                }
                                val commitedit = view.findViewById<EditText>(Id.mime).apply {
                                    hint = getString(R.string.git_commit_msg)
                                    setText("")
                                }
                                MaterialAlertDialogBuilder(activity).setTitle(getString(R.string.push)).setView(view)
                                    .setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.apply)) { _, _ ->
                                        val branch = branchedit.text.toString()
                                        val commit = commitedit.text.toString()
                                        if (branch.isEmpty() || commit.isEmpty()) {
                                            rkUtils.toast(getString(R.string.fill_both))
                                            return@setPositiveButton
                                        }
                                        val loadingPopup = LoadingPopup(activity, null).setMessage(getString(R.string.pushing))
                                        loadingPopup.show()
                                        DefaultScope.launch(Dispatchers.IO) {
                                            try {
                                                val ref = git.repository.findRef(branch)
                                                if (ref == null) {
                                                    git.branchCreate().setName(branch).call()
                                                    git.checkout().setName(branch).call()
                                                } else if (git.repository.branch != branch) {
                                                    git.checkout().setName(branch).call()
                                                }
                                                val config = git.repository.config
                                                config.setString(
                                                    "user",
                                                    null,
                                                    "name",
                                                    userdata[0],
                                                )
                                                config.setString(
                                                    "user",
                                                    null,
                                                    "email",
                                                    userdata[1],
                                                )
                                                config.save()
                                                git.add().addFilepattern(".").call()
                                                git.commit().setMessage(commit).call()
                                                git.push().setCredentialsProvider(
                                                    UsernamePasswordCredentialsProvider(
                                                        credentials[0],
                                                        credentials[1],
                                                    )
                                                ).call()
                                            } catch (e: GitAPIException) {
                                                rkUtils.toast(e.message)
                                            }
                                            withContext(Dispatchers.Main) {
                                                rkUtils.toast(getString(R.string.done))
                                                loadingPopup.hide()
                                            }
                                        }
                                    }.show()
                            } else {
                                rkUtils.toast(getString(R.string.nogit))
                            }
                        }
                    }
                    dialog?.dismiss()
                    dialog = null
                }
                ActionPopup(activity).apply {
                    addItem(
                        getString(R.string.pull),
                        getString(R.string.pull_desc),
                        ContextCompat.getDrawable(activity, R.drawable.sync),
                        pull,
                        listener,
                    )
                    addItem(
                        getString(R.string.commit_push),
                        getString(R.string.push_desc),
                        ContextCompat.getDrawable(activity, R.drawable.upload),
                        push,
                        listener,
                    )
                    setTitle(getString(R.string.git))
                    getDialogBuilder().setNegativeButton(getString(R.string.cancel), null)
                    dialog = show()
                }
                return true
            }
            
            Id.action_add -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.setType("application/octet-stream")
                intent.putExtra(Intent.EXTRA_TITLE, "newfile.txt")
                activity.fileManager.createFileLauncher.launch(intent)
                return true
            }
            
            else -> return false
        }
    }
    
    private fun handleReplace(activity: MainActivity): Boolean {
        val popupView = LayoutInflater.from(activity).inflate(R.layout.popup_replace, null)
        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.replace)).setView(popupView)
            .setNegativeButton(activity.getString(R.string.cancel), null).setPositiveButton(getString(R.string.replaceall)) { _, _ ->
                replaceAll(popupView, activity)
            }.show()
        return true
    }
    
    private fun replaceAll(popupView: View, activity: MainActivity) {
        val editText = popupView.findViewById<EditText>(Id.replace_replacement)
        val text = editText.text.toString()
        val editorFragment = if (activity.adapter.getCurrentFragment()?.fragment is EditorFragment) {
            activity.adapter.getCurrentFragment()?.fragment as EditorFragment
        } else {
            null
        }
        
        editorFragment?.editor?.apply {
            setText(searchText?.let { getText().toString().replace(it, text) })
        }
    }
    
    private fun handleSearchClose(activity: MainActivity): Boolean {
        val editorFragment = if (activity.adapter.getCurrentFragment()?.fragment is EditorFragment) {
            activity.adapter.getCurrentFragment()?.fragment as EditorFragment
        } else {
            null
        }
        searchText = ""
        editorFragment?.editor?.searcher?.stopSearch()
        editorFragment?.editor!!.setSearching(false)
        editorFragment.editor?.invalidate()
        MenuItemHandler.update(activity)
        return true
    }
    
    private fun handleSearch(activity: MainActivity): Boolean {
        val popupView = LayoutInflater.from(activity).inflate(R.layout.popup_search, null)
        val searchBox = popupView.findViewById<EditText>(Id.searchbox)
        
        if (!searchText.isNullOrEmpty()) {
            searchBox.setText(searchText)
        }
        
        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.search)).setView(popupView)
            .setNegativeButton(activity.getString(R.string.cancel), null).setPositiveButton(activity.getString(R.string.search)) { _, _ ->
                // search
                MenuItemHandler.update(activity)
                initiateSearch(activity, searchBox, popupView)
            }.show()
        return true
    }
    
    private fun initiateSearch(activity: MainActivity, searchBox: EditText, popupView: View) {
        searchText = searchBox.text.toString()
        
        if (searchText?.isBlank() == true) {
            return
        }
        
        val editorFragment = if (activity.adapter.getCurrentFragment()?.fragment is EditorFragment) {
            activity.adapter.getCurrentFragment()?.fragment as EditorFragment
        } else {
            null
        }
        // search
        val checkBox = popupView.findViewById<CheckBox>(Id.case_senstive)
        editorFragment?.let {
            it.editor?.searcher?.search(
                searchText!!,
                EditorSearcher.SearchOptions(
                    EditorSearcher.SearchOptions.TYPE_NORMAL,
                    !checkBox.isChecked,
                ),
            )
            it.editor?.setSearching(true)
            MenuItemHandler.update(activity)
        }
    }
}
