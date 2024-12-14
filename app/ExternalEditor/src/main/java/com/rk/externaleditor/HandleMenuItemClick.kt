package com.rk.externaleditor

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.karbon_exec.TERMUX_PREFIX
import com.rk.karbon_exec.launchTermux
import com.rk.karbon_exec.runCommandTermux
import com.rk.libcommons.PathUtils.toPath
import com.rk.libcommons.Printer
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.Runner
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
                    
                    if (PreferencesData.getBoolean(PreferencesKeys.USE_SORA_SEARCH,false)){
                        soraSearch.visibility = View.VISIBLE
                        soraSearch.findViewById<EditText>(com.rk.libcommons.R.id.search_editor).requestFocus()
                        return true
                    }
                    
                    val popuopView = LayoutInflater.from(this).inflate(com.rk.libcommons.R.layout.popup_search, null)
                    val searchBox = popuopView.findViewById<TextView>(com.rk.libcommons.R.id.searchbox)
                    if (searchText.isNotEmpty()) {
                        searchBox.text = searchText
                    }
                    
                    MaterialAlertDialogBuilder(this).setTitle(strings.search.getString()).setView(popuopView)
                        .setNegativeButton(strings.cancel.getString(), null).setPositiveButton(strings.search.getString()) { _, _ ->
                            val checkBox =
                                popuopView.findViewById<CheckBox>(com.rk.libcommons.R.id.case_senstive).also { it.text = strings.cs.getString() }
                            searchText = searchBox.text.toString()
                            editor!!.searcher.search(
                                searchText,
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
                    searchText = ""
                    return true
                }
                
                R.id.replace -> {
                    val popuopView = LayoutInflater.from(this).inflate(com.rk.libcommons.R.layout.popup_replace, null)
                    MaterialAlertDialogBuilder(this).setTitle(strings.replace.getString()).setView(popuopView)
                        .setNegativeButton(strings.cancel.getString(), null).setPositiveButton(strings.replaceall.getString()) { _, _ ->
                            editor!!.searcher.replaceAll(
                                (popuopView.findViewById<View>(com.rk.libcommons.R.id.replace_replacement) as TextView).text.toString()
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
                    if (PreferencesData.getBoolean(PreferencesKeys.FAIL_SAFE, true)) {
                        startActivity(Intent(this, Class.forName("com.rk.xededitor.ui.activities.settings.Terminal")))
                    } else {
                        kotlin.runCatching {
                            launchTermux()
                        }.onFailure {
                            runOnUiThread {
                                Toast.makeText(this,it.message,Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                
                R.id.action_print -> {
                    Printer.print(this, (editor?.text ?: "").toString())
                }
                
                R.id.share -> {
                    try {
                        val sendIntent = Intent()
                        sendIntent.setAction(Intent.ACTION_SEND)
                        sendIntent.putExtra(Intent.EXTRA_TEXT, editor?.text.toString())
                        sendIntent.setType("text/plain")
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        editorActivity.startActivity(shareIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(editorActivity, e.message, Toast.LENGTH_SHORT).show()
                    }
                }
                
                R.id.suggestions -> {
                    editorActivity.editor?.showSuggestions(editorActivity.editor!!.isShowSuggestion().not())
                }
                
                R.id.run -> {
                    val path = intent.data!!.toPath()
                    GlobalScope.launch { Runner.run(File(path), editorActivity) }
                }
                
                else -> return false
            }
        }
        return false
    }
}