package com.rk.xededitor.tab

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
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.Printer
import com.rk.librunner.Runner
import com.rk.xededitor.MainActivity.ActionPopup
import com.rk.xededitor.MainActivity.StaticData.fragments
import com.rk.xededitor.MainActivity.StaticData.mTabLayout
import com.rk.xededitor.MainActivity.file.FileManager
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.Settings.SettingsMainActivity
import com.rk.xededitor.rkUtils
import com.rk.xededitor.terminal.Terminal
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MenuClickHandler {


    private var searchText: String? = ""

    fun handle(activity: TabActivity, menuItem: MenuItem): Boolean {
        val id = menuItem.itemId
        when (id) {

            R.id.run -> {
                activity.getCurrentFragment()?.let { Runner.run(it.file, activity) }
                return true
            }

            R.id.action_all -> {
                activity.tabFragments.values.forEach { f ->
                    f?.save(false)
                }
                rkUtils.toast(activity, "Saved all files")
                return true
            }

            R.id.action_save -> {
                activity.getCurrentFragment()?.save()
                return true
            }

            R.id.undo -> {
                activity.getCurrentFragment()?.undo()
                return true
            }

            R.id.redo -> {
                activity.getCurrentFragment()?.redo()
                return true
            }

            R.id.action_settings -> {
                activity.startActivity(Intent(activity, SettingsMainActivity::class.java))
                return true
            }

            R.id.terminal -> {
                // Handle terminal
                activity.startActivity(Intent(activity, Terminal::class.java))
                return true
            }

            R.id.action_print -> {
                Printer.print(
                    activity, activity.getCurrentFragment()?.editor?.text.toString()
                )
                return true
            }

            R.id.batchrep -> {
                // Handle batchrep
                activity.startActivity(Intent(activity, BatchReplacement::class.java))
                return true
            }

            R.id.search -> {
                // Handle search
                handleSearch(activity)
                return true
            }

            R.id.search_next -> {
                activity.getCurrentFragment()?.editor?.searcher?.gotoNext()
                return true
            }

            R.id.search_previous -> {
                activity.getCurrentFragment()?.editor?.searcher?.gotoPrevious()
                return true
            }

            R.id.search_close -> {
                // Handle search_close
                handleSearchClose(activity)
                return true
            }

            R.id.replace -> {
                // Handle replace
                handleReplace(activity)
                return true
            }

            R.id.share -> {
                rkUtils.shareText(
                    activity, activity.getCurrentFragment()?.editor?.text.toString()
                )
                return true
            }
            R.id.git -> {
                val pull = View.generateViewId()
                val push = View.generateViewId()

                var dialog: AlertDialog? = null
                val credentials = SettingsData.getString(Keys.GIT_CRED, "").split(":")
                if (credentials.size != 2) {
                    rkUtils.toast(activity, "Credentials does not valid. Change it in settings")
                    return true
                }
                val userdata = SettingsData.getString(Keys.GIT_USER_DATA, "").split(":")
                if (userdata.size != 2) {
                    rkUtils.toast(activity, "User data does not valid. Change it in settings")
                    return true
                }
                val listener = View.OnClickListener { v ->
                    when (v.id) {
                        pull -> {
                            val loadingPopup = LoadingPopup(
                                activity,
                                null
                            ).setMessage("Please wait while the files are being downloaded.")
                            loadingPopup.show()
                            GlobalScope.launch(Dispatchers.IO) {
                                try {
                                    val gitRoot =
                                        FileManager.findGitRoot(fragments[mTabLayout.selectedTabPosition].file)
                                    if (gitRoot != null) {
                                        val git = Git.open(gitRoot)
                                        git.pull().setCredentialsProvider(
                                            UsernamePasswordCredentialsProvider(
                                                credentials[0],
                                                credentials[1]
                                            )
                                        ).call()
                                    }
                                } catch (e: GitAPIException) {
                                    rkUtils.toast(activity, e.message)
                                }
                                withContext(Dispatchers.Main) {
                                    rkUtils.toast(activity, "Successfully")
                                    loadingPopup.hide()
                                }
                            }
                        }

                        push -> {
                            val view =
                                LayoutInflater.from(activity).inflate(R.layout.popup_new, null)
                            view.findViewById<LinearLayout>(R.id.mimeTypeEditor).visibility =
                                View.VISIBLE
                            val branchedit = view.findViewById<EditText>(R.id.name).apply {
                                hint = "eg. main"
                            }
                            val commitedit = view.findViewById<EditText>(R.id.mime).apply {
                                hint = "eg. Changed something"
                                setText("Changed something")
                            }
                            MaterialAlertDialogBuilder(activity).setTitle("Push")
                                .setView(view).setNegativeButton("Cancel", null)
                                .setPositiveButton("Apply") { _, _ ->
                                    val branch = branchedit.text.toString()
                                    val commit = commitedit.text.toString()
                                    if (branch.isEmpty() || commit.isEmpty()) {
                                        rkUtils.toast(activity, "Please fill in both fields")
                                        return@setPositiveButton
                                    }
                                    val loadingPopup = LoadingPopup(
                                        activity,
                                        null
                                    ).setMessage("Pushing to remote repository...")
                                    loadingPopup.show()
                                    GlobalScope.launch(Dispatchers.IO) {
                                        try {
                                            val gitRoot =
                                                FileManager.findGitRoot(fragments[mTabLayout.selectedTabPosition].file)
                                            if (gitRoot != null) {
                                                val git = Git.open(gitRoot)
                                                val ref = git.repository.findRef(branch)
                                                if (ref == null) {
                                                    git.branchCreate().setName(branch).call()
                                                    git.checkout().setName(branch).call()
                                                } else if (git.repository.branch != branch) {
                                                    git.checkout().setName(branch).call()
                                                }
                                                val config = git.repository.config
                                                config.setString("user", null, "name", userdata[0])
                                                config.setString("user", null, "email", userdata[1])
                                                config.save()
                                                git.add().addFilepattern(".").call()
                                                git.commit().setMessage(commit).call()
                                                git.push().setCredentialsProvider(
                                                    UsernamePasswordCredentialsProvider(
                                                        credentials[0],
                                                        credentials[1]
                                                    )
                                                ).call()
                                            }
                                        } catch (e: GitAPIException) {
                                            rkUtils.toast(activity, e.message)
                                        }
                                        withContext(Dispatchers.Main) {
                                            rkUtils.toast(activity, "Successfully")
                                            loadingPopup.hide()
                                        }
                                    }
                                }.show()
                        }
                    }
                    dialog?.hide()
                    dialog = null
                }
                ActionPopup(activity).apply {
                    addItem(
                        "Pull",
                        "Sync local repository with remote repository",
                        ContextCompat.getDrawable(activity, R.drawable.sync),
                        listener,
                        pull
                    )
                    addItem(
                        "Commit and push",
                        "Create a commit and push changes to the remote repository",
                        ContextCompat.getDrawable(activity, R.drawable.upload),
                        listener,
                        push
                    )
                    setTitle("Git")
                    getDialogBuilder().setNegativeButton("Cancel", null)
                    dialog = show()
                }
                return true;
            }

            else -> return false
        }


    }

    private fun handleReplace(activity: TabActivity): Boolean {
        val popupView = LayoutInflater.from(activity).inflate(R.layout.popup_replace, null)
        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.replace))
            .setView(popupView).setNegativeButton(activity.getString(R.string.cancel), null)
            .setPositiveButton("replace All") { _, _ ->
                replaceAll(popupView,activity)
            }.show()
        return true
    }

    private fun replaceAll(popupView: View,activity: TabActivity) {
        val editText = popupView.findViewById<EditText>(R.id.replace_replacement)
        val text = editText.text.toString()

        activity.getCurrentFragment()?.editor?.apply {
            setText(searchText?.let { getText().toString().replace(it,text) })
        }

    }


    private fun handleSearchClose(activity: TabActivity): Boolean {
        searchText = ""
        activity.getCurrentFragment()?.editor?.searcher?.stopSearch()
        activity.getCurrentFragment()?.setSearching(false)
        activity.getCurrentFragment()?.editor?.invalidate()
        MenuItemHandler.update(activity)
        return true
    }

    private fun handleSearch(activity: TabActivity): Boolean {
        val popupView = LayoutInflater.from(activity).inflate(R.layout.popup_search, null)
        val searchBox = popupView.findViewById<EditText>(R.id.searchbox)

        if (!searchText.isNullOrEmpty()) {
            searchBox.setText(searchText)
        }

        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.search))
            .setView(popupView).setNegativeButton(activity.getString(R.string.cancel), null)
            .setPositiveButton(activity.getString(R.string.search)) { _, _ ->
                //search
                MenuItemHandler.update(activity)
                initiateSearch(activity, searchBox, popupView)
            }.show()
        return true
    }


    private fun initiateSearch(activity: TabActivity, searchBox: EditText, popupView: View) {
        searchText = searchBox.text.toString()

        if (searchText?.isBlank() == true) {
            return
        }

        //search
        val checkBox = popupView.findViewById<CheckBox>(R.id.case_senstive)
        activity.getCurrentFragment()?.let {
            it.editor?.searcher?.search(
                searchText!!, EditorSearcher.SearchOptions(
                    EditorSearcher.SearchOptions.TYPE_NORMAL, !checkBox.isChecked
                )
            )
            it.setSearching(true)
            MenuItemHandler.update(activity)
        }

    }

}
