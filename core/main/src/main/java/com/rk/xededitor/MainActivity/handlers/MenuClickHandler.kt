package com.rk.xededitor.MainActivity.handlers

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
import com.rk.controlpanel.ControlItem
import com.rk.file_wrapper.FileWrapper
import com.rk.launchTermux
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.Printer
import com.rk.libcommons.alpineHomeDir
import com.rk.libcommons.application
import com.rk.libcommons.askInput
import com.rk.libcommons.child
import com.rk.libcommons.composeDialog
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.runOnUiThread
import com.rk.libcommons.safeLaunch
import com.rk.libcommons.toast
import com.rk.libcommons.toastCatching
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.Runner
import com.rk.settings.Settings
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.editor.getCurrentEditorFragment
import com.rk.xededitor.MainActivity.tabs.editor.saveAllFiles
import com.rk.xededitor.R
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import com.rk.xededitor.ui.activities.terminal.Terminal
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.launch

typealias Id = R.id

object MenuClickHandler {

    private var searchText: String? = ""

    suspend fun handle(activity: MainActivity, menuItem: MenuItem): Boolean {
        val id = menuItem.itemId

        when (id) {
            Id.saveAs -> {
                getCurrentEditorFragment()?.file?.let {
                    activity.fileManager?.saveAsFile(it)
                }
                return true
            }

            Id.run -> {
                val file =
                    MainActivity.activityRef.get()?.adapter?.getCurrentFragment()?.fragment?.getFile()
                if (file == null) {
                    toast("Illegal state")
                    return true
                }
                DefaultScope.safeLaunch {
                    Runner.run(file, activity)
                }
                return true
            }

            Id.action_all -> {
                saveAllFiles()
                toast(strings.save_all.getString())
                return true
            }

            Id.action_save -> {
                getCurrentEditorFragment()?.save(false)
                return true
            }

            Id.undo -> {
                getCurrentEditorFragment()?.undo()
                return true
            }

            Id.redo -> {
                getCurrentEditorFragment()?.redo()
                return true
            }

            Id.action_settings -> {
                activity.startActivity(Intent(activity, SettingsActivity::class.java))
                return true
            }

            Id.terminal -> {
                val runtime = Settings.terminal_runtime
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
                printer.setCodeText(
                    getCurrentEditorFragment()?.editor?.text.toString(),
                    language = getCurrentEditorFragment()?.file?.getName()?.substringAfterLast(".")
                        ?.trim() ?: "txt"
                )
                return true
            }

            Id.search -> {
                // Handle search

                if (Settings.use_sora_search) {
                    getCurrentEditorFragment()?.showSearch(true)
                } else {
                    handleSearch(activity)
                }

                return true
            }

            Id.search_next -> {
                getCurrentEditorFragment()?.editor?.searcher?.gotoNext()
                return true
            }

            Id.search_previous -> {
                getCurrentEditorFragment()?.editor?.searcher?.gotoPrevious()
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
                getCurrentEditorFragment()?.refreshEditorContent()
                return true
            }

            Id.share -> {
                toastCatching {

                    val file =
                        MainActivity.activityRef.get()?.adapter?.getCurrentFragment()?.fragment?.getFile()
                    if (file == null) {
                        toast(strings.unsupported_contnt)
                        return true
                    }

                    if (file is FileWrapper) {
                        if (file.getAbsolutePath()
                                .contains(application!!.filesDir.parentFile!!.absolutePath)
                        ) {
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
                composeDialog { dialog ->
                    ControlItem(
                        item = ControlItem(
                            label = strings.tempFile.getString(),
                            sideEffect = {
                                dialog?.dismiss()
                                activity.adapter!!.addFragment(
                                    FileWrapper(
                                        alpineHomeDir().child("temp.txt").createFileIfNot()
                                    )
                                )

                            }
                        )
                    )

                    ControlItem(
                        item = ControlItem(
                            label = strings.new_file.getString(),
                            sideEffect = {
                                dialog?.dismiss()
                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                                intent.addCategory(Intent.CATEGORY_OPENABLE)
                                intent.setType("application/octet-stream")
                                intent.putExtra(Intent.EXTRA_TITLE, "newfile.txt")

                                val activities = application!!.packageManager.queryIntentActivities(
                                    intent,
                                    PackageManager.MATCH_ALL
                                )
                                if (activities.isNotEmpty()) {
                                    activity.fileManager!!.createFileLauncher.launch(intent)
                                } else {
                                    activity.askInput(
                                        title = strings.new_file.getString(),
                                        hint = "newfile.txt",
                                        onResult = { input ->
                                            activity.fileManager?.selectDirForNewFileLaunch(input)
                                        })
                                }
                            })
                    )

                    ControlItem(
                        item = ControlItem(label = strings.openfile.getString(),
                            sideEffect = {
                                dialog?.dismiss()
                                activity.fileManager?.requestOpenFile()
                            })
                    )

                }

                return true
            }

            Id.toggle_word_wrap -> {
                (activity.adapter?.getCurrentFragment()?.fragment as? EditorFragment)?.editor?.let {
                    it.isWordwrap = it.isWordwrap.not()
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
