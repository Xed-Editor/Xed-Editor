package com.rk.xededitor.MainActivity.handlers

import android.content.Intent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.Printer
import com.rk.librunner.Runner
import com.rk.xededitor.MainActivity.ActionPopup
import com.rk.xededitor.MainActivity.BatchReplacement.BatchReplacement
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.MainActivity.StaticData.fragments
import com.rk.xededitor.MainActivity.StaticData.mTabLayout
import com.rk.xededitor.MainActivity.file.FileManager
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
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.text.SimpleDateFormat
import java.util.Date

object MenuClickHandler {


    private var searchText: String? = ""
    private val pull = View.generateViewId()
    private val push = View.generateViewId()

    fun handle(activity: MainActivity, menuItem: MenuItem): Boolean {
        val id = menuItem.itemId
        when (id) {

            R.id.run -> {
                FileManager.saveFile(activity, fragments[mTabLayout.selectedTabPosition], true)
                fragments[mTabLayout.selectedTabPosition].file?.let { Runner.run(it, activity) }
                return true
            }

            R.id.git -> {
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

            R.id.action_all -> {
                // Handle action_all
                FileManager.handleSaveAllFiles(activity)
                return true
            }

            R.id.action_save -> {
                // Handle action_save
                FileManager.saveFile(activity, fragments[mTabLayout.selectedTabPosition])
                return true
            }

            R.id.undo -> {
                // Handle undo
                fragments[mTabLayout.selectedTabPosition].Undo()
                updateUndoRedoMenuItems()
                return true
            }

            R.id.redo -> {
                // Handle redo
                fragments[mTabLayout.selectedTabPosition].Redo()
                updateUndoRedoMenuItems()
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
                // Handle action_print
                Printer.print(
                    activity,
                    fragments[mTabLayout.selectedTabPosition].content.toString()
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
                // Handle search_next
                handleSearchNext()
                return true
            }

            R.id.search_previous -> {
                // Handle search_previous
                handleSearchPrevious()
                return true
            }

            R.id.search_close -> {
                // Handle search_close
                handleSearchClose()
                return true
            }

            R.id.replace -> {
                // Handle replace
                handleReplace(activity)
                return true
            }

            R.id.share -> {
                // Handle share
                rkUtils.shareText(activity, rkUtils.currentEditor?.text.toString())
                return true
            }

            R.id.insertdate -> {
                // Handle insertdate
                rkUtils.currentEditor?.pasteText(
                    " " + SimpleDateFormat.getDateTimeInstance()
                        .format(Date(System.currentTimeMillis())) + " "
                )
                return true
            }

            else -> return false
        }


    }


    private fun updateUndoRedoMenuItems() {
        val undo = StaticData.menu.findItem(R.id.undo)
        val redo = StaticData.menu.findItem(R.id.redo)
        val editor = fragments[mTabLayout.selectedTabPosition].editor
        redo.isEnabled = editor.canRedo() == true
        undo.isEnabled = editor.canUndo() == true
    }

    private fun handleReplace(activity: MainActivity): Boolean {
        val popupView = LayoutInflater.from(activity).inflate(R.layout.popup_replace, null)
        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.replace))
            .setView(popupView).setNegativeButton(activity.getString(R.string.cancel), null)
            .setPositiveButton("replace All") { _, _ ->
                replaceAll(popupView)
            }.show()
        return true
    }

    private fun replaceAll(popupView: View) {
        val replacementText =
            popupView.findViewById<TextView>(R.id.replace_replacement).text.toString()
        fragments[mTabLayout.selectedTabPosition].editor.searcher?.replaceAll(replacementText)
    }


    private fun handleSearchNext(): Boolean {
        fragments[mTabLayout.selectedTabPosition].editor.searcher?.gotoPrevious()
        return true
    }

    private fun handleSearchPrevious(): Boolean {
        fragments[mTabLayout.selectedTabPosition].editor.searcher?.gotoNext()
        return true
    }

    private fun handleSearchClose(): Boolean {
        if (mTabLayout.selectedTabPosition != -1) {
            val fragment = fragments[mTabLayout.selectedTabPosition]
            fragment.isSearching = false
            fragment.editor.searcher?.stopSearch()
        }

        hideSearchMenuItems()
        searchText = ""
        return true
    }

    private fun handleSearch(activity: MainActivity): Boolean {
        val popupView = LayoutInflater.from(activity).inflate(R.layout.popup_search, null)
        val searchBox = popupView.findViewById<EditText>(R.id.searchbox)

        if (!searchText.isNullOrEmpty()) {
            searchBox.setText(searchText)
        }

        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.search))
            .setView(popupView).setNegativeButton(activity.getString(R.string.cancel), null)
            .setPositiveButton(activity.getString(R.string.search)) { _, _ ->
                //search
                initiateSearch(searchBox, popupView)
            }.show()
        return true
    }

    private fun initiateSearch(searchBox: EditText, popupView: View) {
        searchText = searchBox.text.toString()

        if (searchText?.isBlank() == true) {
            return
        }

        val fragment = fragments[mTabLayout.selectedTabPosition]
        fragment.isSearching = true
        val checkBox = popupView.findViewById<CheckBox>(R.id.case_senstive)
        fragment.editor.searcher?.search(
            searchText!!,
            EditorSearcher.SearchOptions(
                EditorSearcher.SearchOptions.TYPE_NORMAL,
                !checkBox.isChecked
            )
        )
        showSearchMenuItems()
    }


    fun showSearchMenuItems() {
        with(StaticData.menu) {
            findItem(R.id.search_next).isVisible = true
            findItem(R.id.search_previous).isVisible = true
            findItem(R.id.search_close).isVisible = true
            findItem(R.id.replace).isVisible = true
            findItem(R.id.undo).isVisible = false
            findItem(R.id.redo).isVisible = false
            findItem(R.id.run).isVisible = false
            findItem(R.id.git).isVisible = false
        }

    }

    fun hideSearchMenuItems() {
        with(StaticData.menu) {
            findItem(R.id.search_next).isVisible = false
            findItem(R.id.search_previous).isVisible = false
            findItem(R.id.search_close).isVisible = false
            findItem(R.id.replace).isVisible = false

            val v = !(mTabLayout.selectedTabPosition == -1 && fragments.isNullOrEmpty())
            findItem(R.id.run).isVisible =
                v && Runner.isRunnable(fragments[mTabLayout.selectedTabPosition].file!!)
            findItem(R.id.git).isVisible =
                v && FileManager.findGitRoot(fragments[mTabLayout.selectedTabPosition].file) != null

            if (mTabLayout.selectedTabPosition != -1) {
                findItem(R.id.undo).isVisible = true
                findItem(R.id.redo).isVisible = true
            }
        }


    }

}
