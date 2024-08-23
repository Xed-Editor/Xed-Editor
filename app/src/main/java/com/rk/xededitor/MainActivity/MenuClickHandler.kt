package com.rk.xededitor.MainActivity

import android.content.Intent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.After
import com.rk.libcommons.Printer
import com.rk.librunner.Runner
import com.rk.xededitor.BatchReplacement.BatchReplacement
import com.rk.xededitor.MainActivity.StaticData.fragments
import com.rk.xededitor.MainActivity.StaticData.mTabLayout
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsMainActivity
import com.rk.xededitor.rkUtils
import com.rk.xededitor.terminal.Terminal
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.EditorSearcher
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

object MenuClickHandler {
	
	
	private var searchText: String? = ""
	
	fun handle(activity: MainActivity, menuItem: MenuItem): Boolean {
		val id = menuItem.itemId
		when (id) {
			
			R.id.run -> {
				fragments[mTabLayout.selectedTabPosition].file?.let { Runner.run(it, activity) }
				return true
			}
			
			R.id.action_all -> {
				// Handle action_all
				handleSaveAll(activity)
				return true
			}
			
			R.id.action_save -> {
				// Handle action_save
				saveFile(activity, mTabLayout.selectedTabPosition)
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
				Printer.print(activity, fragments[mTabLayout.selectedTabPosition].content.toString())
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
				rkUtils.shareText(activity, rkUtils.currentEditor.text.toString())
				return true
			}
			
			R.id.insertdate -> {
				// Handle insertdate
				rkUtils.currentEditor.pasteText(" " + SimpleDateFormat.getDateTimeInstance().format(Date(System.currentTimeMillis())) + " ")
				return true
			}
			
			else -> return false
		}
		
		
	}
	
	
	private fun updateUndoRedoMenuItems() {
		val undo = StaticData.menu.findItem(R.id.undo)
		val redo = StaticData.menu.findItem(R.id.redo)
		val editor = fragments[mTabLayout.selectedTabPosition].editor
		redo.isEnabled = editor.canRedo()
		undo.isEnabled = editor.canUndo()
	}
	
	private fun handleReplace(activity: MainActivity): Boolean {
		val popupView = LayoutInflater.from(activity).inflate(R.layout.popup_replace, null)
		MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.replace)).setView(popupView).setNegativeButton(activity.getString(R.string.cancel), null)
			.setPositiveButton("replace All") { _, _ ->
				replaceAll(popupView)
			}.show()
		return true
	}
	
	private fun replaceAll(popupView: View) {
		val replacementText = popupView.findViewById<TextView>(R.id.replace_replacement).text.toString()
		fragments[mTabLayout.selectedTabPosition].editor.searcher.replaceAll(replacementText)
	}
	
	
	private fun handleSearchNext(): Boolean {
		fragments[mTabLayout.selectedTabPosition].editor.searcher.gotoNext()
		return true
	}
	
	private fun handleSearchPrevious(): Boolean {
		fragments[mTabLayout.selectedTabPosition].editor.searcher.gotoPrevious()
		return true
	}
	
	private fun handleSearchClose(): Boolean {
		if (mTabLayout.selectedTabPosition != -1) {
			val fragment = fragments[mTabLayout.selectedTabPosition]
			fragment.isSearching = false
			fragment.editor.searcher.stopSearch()
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
		
		MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.search)).setView(popupView).setNegativeButton(activity.getString(R.string.cancel), null)
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
		fragment.editor.searcher.search(searchText!!, EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, !checkBox.isChecked))
		showSearchMenuItems()
	}
	
	
	private fun saveFile(activity: MainActivity, index: Int, isAutoSaver: Boolean = false) {
		val fragment = fragments[index]
		val file = fragment.file
		val content = fragment.content
		
		rkUtils.runOnUiThread {
			val tab = mTabLayout.getTabAt(index) ?: throw RuntimeException("Tab not found")
			if (tab.text?.endsWith("*") == true) {
				fragment.isModified = false
				tab.text = tab.text?.dropLast(1)
			}
		}
		
		Thread {
			val outputStream = FileOutputStream(file, false)
			if (content != null) {
				ContentIO.writeTo(content, outputStream, true)
			}
		}.start()
		
		if (!isAutoSaver) {
			rkUtils.toast(activity, activity.getString(R.string.save))
		}
		
		
	}
	
	fun handleSaveAll(activity: MainActivity, isAutoSaver: Boolean = false) {
		//loop over all tabs and save the files
		for (i in 0 until mTabLayout.tabCount) {
			saveFile(activity, i, isAutoSaver)
		}
		
		if (!isAutoSaver) {
			After(100) {
				rkUtils.runOnUiThread {
					rkUtils.toast(activity, activity.getString(R.string.saveAll))
				}
			}
		}
		
		
	}
	
	fun showSearchMenuItems() {
		StaticData.menu.findItem(R.id.search_next).isVisible = true
		StaticData.menu.findItem(R.id.search_previous).isVisible = true
		StaticData.menu.findItem(R.id.search_close).isVisible = true
		StaticData.menu.findItem(R.id.replace).isVisible = true
		
		StaticData.menu?.findItem(R.id.undo)?.setVisible(false)
		StaticData.menu?.findItem(R.id.redo)?.setVisible(false)
	}
	
	fun hideSearchMenuItems() {
		StaticData.menu.findItem(R.id.search_next).isVisible = false
		StaticData.menu.findItem(R.id.search_previous).isVisible = false
		StaticData.menu.findItem(R.id.search_close).isVisible = false
		StaticData.menu.findItem(R.id.replace).isVisible = false
		
		if (mTabLayout.selectedTabPosition != -1) {
			StaticData.menu?.findItem(R.id.undo)?.setVisible(true)
			StaticData.menu?.findItem(R.id.redo)?.setVisible(true)
		}
		
	}
	
}
