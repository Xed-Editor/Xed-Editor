package com.rk.externaleditor.SimpleEditor

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.externaleditor.R
import com.rk.libcommons.PathUtils.toPath
import com.rk.libcommons.Printer
import com.rk.libcommons.rkUtils2
import com.rk.runner.Runner
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import java.io.File

object HandleMenuItemClick {
    fun handle(editorActivity: SimpleEditor, id: Int): Boolean {
        with(editorActivity) {
            when (id) {
                android.R.id.home -> {
                    onBackPressedDispatcher.onBackPressed()
                    return true
                }
                
                R.id.action_settings -> {
                    startActivity(Intent(this, Class.forName("com.rk.xededitor.ui.activities.settings.SettingsActivity")))
                }
                
                R.id.action_save -> {
                    save()
                    return true
                }
                
                R.id.search -> {
                    val popuopView = LayoutInflater.from(this).inflate(R.layout.popup_search, null)
                    val searchBox = popuopView.findViewById<TextView>(R.id.searchbox)
                    if (SearchText.isNotEmpty()) {
                        searchBox.text = SearchText
                    }
                    
                    MaterialAlertDialogBuilder(this).setTitle("Search").setView(popuopView)
                        .setNegativeButton("Cancel", null).setPositiveButton("Cancel") { _, _ ->
                            val checkBox = popuopView.findViewById<CheckBox>(R.id.case_senstive)
                            SearchText = searchBox.text.toString()
                            editor!!.searcher.search(
                                SearchText,
                                SearchOptions(SearchOptions.TYPE_NORMAL, !checkBox.isChecked),
                            )
                            menu!!.findItem(R.id.search_next).setVisible(true)
                            menu!!.findItem(R.id.search_previous).setVisible(true)
                            menu!!.findItem(R.id.search_close).setVisible(true)
                            menu!!.findItem(R.id.replace).setVisible(true)
                        }.show()
                }
                
                R.id.search_next -> {
                    editor!!.searcher.gotoNext()
                    return true
                }
                
                R.id.search_previous -> {
                    editor!!.searcher.gotoPrevious()
                    return true
                }
                
                R.id.search_close -> {
                    editor!!.searcher.stopSearch()
                    menu!!.findItem(R.id.search_next).setVisible(false)
                    menu!!.findItem(R.id.search_previous).setVisible(false)
                    menu!!.findItem(R.id.search_close).setVisible(false)
                    menu!!.findItem(R.id.replace).setVisible(false)
                    SearchText = ""
                    return true
                }
                
                R.id.replace -> {
                    val popuopView = LayoutInflater.from(this).inflate(R.layout.popup_replace, null)
                    MaterialAlertDialogBuilder(this).setTitle("Replace").setView(popuopView)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("ReplaceAll") { _, _ ->
                            editor!!.searcher.replaceAll(
                                (popuopView.findViewById<View>(R.id.replace_replacement) as TextView).text.toString()
                            )
                        }.show()
                }
                
                R.id.undo -> {
                    if (editor!!.canUndo()) {
                        editor!!.undo()
                    }
                    redo!!.setEnabled(editor!!.canRedo())
                    undo!!.setEnabled(editor!!.canUndo())
                }
                
                R.id.redo -> {
                    if (editor!!.canRedo()) {
                        editor!!.redo()
                    }
                    redo!!.setEnabled(editor!!.canRedo())
                    undo!!.setEnabled(editor!!.canUndo())
                }
                
                
                R.id.terminal -> {
                    startActivity(Intent(this, Class.forName("com.rk.xededitor.terminal.Terminal")))
                }
                
                R.id.action_print -> {
                    Printer.print(this, (editor?.text ?: "").toString())
                }
                
                R.id.share -> {
                    rkUtils2.shareText(this, (editor?.text ?: "").toString())
                }
                
                R.id.suggestions -> {
                    editorActivity.editor?.showSuggestions(editorActivity.editor!!.isShowSuggestion().not())
                }
                
                R.id.run -> {
                    val path = intent.data!!.toPath()
                    Runner.run(File(path), editorActivity)
                }
                
                else -> return false
            }
        }
        return false
    }
}
