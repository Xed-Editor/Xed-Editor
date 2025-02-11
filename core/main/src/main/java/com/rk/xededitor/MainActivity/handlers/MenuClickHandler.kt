package com.rk.xededitor.MainActivity.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.file_wrapper.FileWrapper
import com.rk.karbon_exec.launchTermux
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.Printer
import com.rk.libcommons.application
import com.rk.libcommons.askInput
import com.rk.libcommons.runOnUiThread
import com.rk.libcommons.safeLaunch
import com.rk.libcommons.toast
import com.rk.libcommons.toastCatching
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.Runner
import com.rk.settings.Settings
import com.rk.settings.SettingsKey
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.file.FileManager.Companion.findGitRoot
import com.rk.xededitor.git.commit
import com.rk.xededitor.git.pull
import com.rk.xededitor.git.push
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.R
import com.rk.xededitor.git.GitClient
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import com.rk.xededitor.ui.activities.terminal.Terminal
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

typealias Id = R.id

object MenuClickHandler {

    private var searchText: String? = ""

    fun getEditorFragment():EditorFragment?{
       return MainActivity.activityRef.get()?.adapter?.getCurrentFragment()?.fragment as? EditorFragment
    }
    
    suspend fun handle(activity: MainActivity, menuItem: MenuItem): Boolean {
        val id = menuItem.itemId

        when (id) {
            Id.saveAs -> {
                getEditorFragment()?.file?.let {
                    activity.fileManager?.saveAsFile(it)
                }
                return true
            }

            Id.run -> {
                val file = MainActivity.activityRef.get()?.adapter?.getCurrentFragment()?.fragment?.getFile()
                if (file == null){
                    toast("Illegal state")
                    return true
                }
                DefaultScope.safeLaunch {
                    Runner.run(file, activity)
                }
                return true
            }

            Id.action_all -> {
                activity.adapter!!.tabFragments.values.forEach { f ->
                    if (f.get()?.fragment is EditorFragment) {
                        (f.get()?.fragment as EditorFragment).save(false)
                    }
                }
                toast(strings.save_all.getString())
                return true
            }

            Id.action_save -> {
                getEditorFragment()?.save(true)
                return true
            }

            Id.undo -> {
                getEditorFragment()?.undo()
                return true
            }

            Id.redo -> {
                getEditorFragment()?.redo()
                return true
            }

            Id.action_settings -> {
                activity.startActivity(Intent(activity, SettingsActivity::class.java))
                return true
            }

            Id.terminal -> {
                val runtime = Settings.getString(SettingsKey.TERMINAL_RUNTIME, "Alpine")
                if (runtime == "Termux") {
                    kotlin.runCatching {
                        launchTermux()
                    }.onFailure {
                        runOnUiThread {
                            Toast.makeText(activity, it.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    activity.startActivity(
                        Intent(
                            activity, Terminal::class.java
                        )
                    )

                }
                return true
            }

            Id.action_print -> {
                val printer = Printer(activity)
                printer.setCodeText(getEditorFragment()?.editor?.text.toString(), language = getEditorFragment()?.file?.getName()?.substringAfterLast(".")?.trim() ?: "txt")
                return true
            }

            //garbage code

            Id.search -> {
                // Handle search

                if (Settings.getBoolean(SettingsKey.USE_SORA_SEARCH, true)) {
                    val fragment =
                        MainActivity.activityRef.get()?.adapter?.getCurrentFragment()?.fragment

                    if (fragment is EditorFragment) {
                        fragment.showSearch(true)
                    }
                } else {
                    handleSearch(activity)
                }

                return true
            }

            Id.search_next -> {
                getEditorFragment()?.editor?.searcher?.gotoNext()
                return true
            }

            Id.search_previous -> {
                getEditorFragment()?.editor?.searcher?.gotoPrevious()
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

            Id.refreshEditor -> {
                getEditorFragment()?.refreshEditorContent()
                return true
            }

            Id.share -> {
                toastCatching {

                    val file = MainActivity.activityRef.get()?.adapter?.getCurrentFragment()?.fragment?.getFile()
                    if (file == null){
                        toast(strings.unsupported_contnt)
                        return true
                    }

                    if (file is FileWrapper){
                        if (file.getAbsolutePath().contains(application!!.filesDir.parentFile!!.absolutePath)){
                            //files in private directory cannot be shared
                            toast(strings.permission_denied)
                            return true
                        }
                    }

                    val fileUri = if (file is FileWrapper) {
                        FileProvider.getUriForFile(
                            activity,
                            "${activity.packageName}.fileprovider",
                            file.file
                        )
                    } else {
                        file.toUri()
                    }

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = activity.contentResolver.getType(fileUri) ?: "*/*"
                        setDataAndType(fileUri, activity.contentResolver.getType(fileUri) ?: "*/*")
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }

                    activity.startActivity(Intent.createChooser(intent, "Share file"))
                }
                return true
            }

            Id.suggestions -> {
                menuItem.isChecked = menuItem.isChecked.not()
                activity.adapter!!.tabFragments.values.forEach { f ->
                    if (f.get()?.fragment is EditorFragment) {
                        (f.get()?.fragment as EditorFragment).editor?.showSuggestions(menuItem.isChecked)
                    }
                }
                return true
            }

            Id.action_add -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.setType("application/octet-stream")
                intent.putExtra(Intent.EXTRA_TITLE, "newfile.txt")

                val activities = application!!.packageManager.queryIntentActivities(intent,PackageManager.MATCH_ALL)
                if (activities.isNotEmpty()){
                    activity.fileManager!!.createFileLauncher.launch(intent)
                }else{
                    activity.askInput(title = "Create File", hint = "newfile.txt", onResult = { input ->
                        activity.fileManager?.selectDirForNewFileLaunch(input)
                    })
                }
                return true
            }

            Id.action_pull -> {
                activity.adapter!!.getCurrentFragment()?.fragment?.let {
                    if (it is EditorFragment && it.file is FileWrapper) {
                        it.file?.let { it1 -> pull(activity, (it1 as FileWrapper).file) }
                    } else {
                        throw RuntimeException("wtf just happened?")
                    }
                }
            }

            Id.action_push -> {
                activity.adapter!!.getCurrentFragment()?.fragment?.let {
                    if (it is EditorFragment && it.file is FileWrapper) {
                        it.file?.let { it1 -> push(activity, (it1 as FileWrapper).file) }
                    } else {
                        throw RuntimeException("wtf just happened?")
                    }
                }
            }

            Id.action_commit -> {
                activity.adapter!!.getCurrentFragment()?.fragment?.let {
                    if (it is EditorFragment && it.file is FileWrapper) {
                        it.file?.let { it1 -> commit(activity, (it1 as FileWrapper).file) }
                    } else {
                        throw RuntimeException("wtf just happened?")
                    }
                }
            }

            Id.action_branch -> {
                fun showRadioButtonDialog(
                    context: Context,
                    title: String,
                    items: List<String>,
                    defaultSelection: String,
                    onItemSelected: (String) -> Unit
                ) {
                    var selectedItem = defaultSelection
                    val checkedItem = items.indexOf(defaultSelection)

                    MaterialAlertDialogBuilder(context).setTitle(title).setSingleChoiceItems(
                            items.toTypedArray(), checkedItem
                        ) { _, which ->
                            selectedItem = items[which]
                        }.setPositiveButton("OK") { _, _ ->
                            onItemSelected(selectedItem)
                        }.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }.show()
                }


                activity.adapter!!.getCurrentFragment()?.fragment?.let {
                    if (it is EditorFragment && it.file is FileWrapper) {
                        DefaultScope.launch(Dispatchers.IO) {

                            it.file?.let { it1 ->
                                findGitRoot((it1 as FileWrapper).file)?.let { root ->
                                    GitClient.getAllBranches(
                                        activity,
                                        root,
                                        onResult = { branches, eror ->
                                            GitClient.getCurrentBranchFull(activity,
                                                root,
                                                onResult = { branch, erorr ->
                                                    runOnUiThread {
                                                        if (branches != null) {
                                                            if (branch != null) {
                                                                showRadioButtonDialog(
                                                                    activity,
                                                                    title = "Branches",
                                                                    items = branches,
                                                                    defaultSelection = branch,
                                                                    onItemSelected = { selectedBranch ->
                                                                        DefaultScope.launch(
                                                                            Dispatchers.IO
                                                                        ) {
                                                                            GitClient.setBranch(
                                                                                activity,
                                                                                root,
                                                                                selectedBranch,
                                                                                {})
                                                                        }
                                                                    },
                                                                )
                                                            }
                                                        }
                                                    }
                                                })

                                        })
                                }
                            }
                        }

                    } else {
                        throw RuntimeException("wtf just happened?")
                    }
                }


            }

        }

        return false
    }

    private fun handleReplace(activity: MainActivity): Boolean {
        val popupView =
            LayoutInflater.from(activity).inflate(R.layout.popup_replace, null)
        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(strings.replace))
            .setView(popupView).setNegativeButton(activity.getString(strings.cancel), null)
            .setPositiveButton(strings.replaceall) { _, _ ->
                replaceAll(popupView, activity)
            }.show()
        return true
    }

    private fun replaceAll(popupView: View, activity: MainActivity) {
        val editText = popupView.findViewById<EditText>(Id.replace_replacement)
        val text = editText.text.toString()
        val editorFragment =
            if (activity.adapter!!.getCurrentFragment()?.fragment is EditorFragment) {
                activity.adapter!!.getCurrentFragment()?.fragment as EditorFragment
            } else {
                null
            }

        editorFragment?.editor?.apply {
            setText(searchText?.let { getText().toString().replace(it, text) })
        }
    }

    private fun handleSearchClose(activity: MainActivity): Boolean {
        val editorFragment =
            if (activity.adapter!!.getCurrentFragment()?.fragment is EditorFragment) {
                activity.adapter!!.getCurrentFragment()?.fragment as EditorFragment
            } else {
                null
            }
        searchText = ""
        editorFragment?.editor?.searcher?.stopSearch()
        editorFragment?.editor!!.setSearching(false)
        editorFragment.editor?.invalidate()
        DefaultScope.launch { updateMenu(MainActivity.activityRef.get()?.adapter?.getCurrentFragment()) }

        return true
    }

    private fun handleSearch(activity: MainActivity): Boolean {
        val popupView =
            LayoutInflater.from(activity).inflate(R.layout.popup_search, null)
        val searchBox = popupView.findViewById<EditText>(R.id.searchbox)

        if (!searchText.isNullOrEmpty()) {
            searchBox.setText(searchText)
        }

        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(strings.search))
            .setView(popupView).setNegativeButton(activity.getString(strings.cancel), null)
            .setPositiveButton(activity.getString(strings.search)) { _, _ ->
                // search
                DefaultScope.launch { updateMenu(MainActivity.activityRef.get()?.adapter?.getCurrentFragment()) }

                initiateSearch(activity, searchBox, popupView)
            }.show()
        return true
    }

    private fun initiateSearch(activity: MainActivity, searchBox: EditText, popupView: View) {
        searchText = searchBox.text.toString()

        if (searchText?.isBlank() == true) {
            return
        }

        val editorFragment =
            if (activity.adapter!!.getCurrentFragment()?.fragment is EditorFragment) {
                activity.adapter!!.getCurrentFragment()?.fragment as EditorFragment
            } else {
                null
            }
        // search
        val checkBox = popupView.findViewById<CheckBox>(R.id.case_senstive)
            .also { it.text = strings.cs.getString() }
        editorFragment?.let {
            it.editor?.searcher?.search(
                searchText!!,
                EditorSearcher.SearchOptions(
                    EditorSearcher.SearchOptions.TYPE_NORMAL,
                    !checkBox.isChecked,
                ),
            )
            it.editor?.setSearching(true)
            DefaultScope.launch { updateMenu(MainActivity.activityRef.get()?.adapter?.getCurrentFragment()) }

        }
    }
}
