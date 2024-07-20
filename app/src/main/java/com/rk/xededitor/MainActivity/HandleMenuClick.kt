package com.rk.xededitor.MainActivity

import android.content.Intent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.app.ActivityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.xededitor.After
import com.rk.xededitor.BatchReplacement.BatchReplacement
import com.rk.xededitor.MainActivity.StaticData.REQUEST_CODE_CREATE_FILE
import com.rk.xededitor.MainActivity.StaticData.fragments
import com.rk.xededitor.MainActivity.StaticData.mTabLayout
import com.rk.xededitor.Printer
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsApp
import com.rk.xededitor.Settings.SettingsBaseActivity
import com.rk.xededitor.Settings.SettingsMainActivity
import com.rk.xededitor.rkUtils
import com.rk.xededitor.terminal.Terminal
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date

class HandleMenuClick {
  companion object {
    @JvmStatic
    private var searchText: String? = ""
    
    @JvmStatic
    fun handle(activity: MainActivity, item: MenuItem): Boolean {
      return when (item.itemId) {
        R.id.action_save -> handleSave(activity)
        R.id.action_settings -> startSettingsActivity(activity)
        R.id.action_all -> handleSaveAll(activity)
        R.id.search -> handleSearch(activity)
        R.id.search_next -> handleSearchNext(activity)
        R.id.search_previous -> handleSearchPrevious(activity)
        R.id.search_close -> handleSearchClose(activity)
        R.id.replace -> handleReplace(activity)
        R.id.batchrep -> startBatchReplacementActivity(activity)
        R.id.undo -> handleUndo(activity)
        R.id.redo -> handleRedo(activity)
        R.id.action_print -> handlePrint(activity)
        R.id.share -> handleShare(activity)
        R.id.insertdate -> insertDate(activity)
        R.id.terminal -> launch_terminal(activity)
        else -> false
      }
    }
    
    private fun launch_terminal(activity: MainActivity) : Boolean{
      
      activity.startActivity(Intent(activity, Terminal::class.java))
      
      return true
    }
    
    private fun insertDate(activity: MainActivity): Boolean {
      activity.currentEditor.pasteText(
        " " + SimpleDateFormat.getDateTimeInstance().format(Date(System.currentTimeMillis())) + " "
      )
      return true
    }
    
    private fun handleSave(activity: MainActivity): Boolean {
      if (fragments.isEmpty()) return true
      
      val index = mTabLayout.selectedTabPosition
      val fragment = fragments[index]
      fragment.isModified = false
      mTabLayout.getTabAt(index)?.text = fragment.fileName
      
      if (fragment.isNewFile) {
        saveNewFile(activity, fragment)
      } else {
        saveExistingFile(activity, index)
      }
      return true
    }
    
    private fun saveNewFile(activity: MainActivity, fragment: DynamicFragment) {
      Thread {
        try {
          val outputStream = File(activity.externalCacheDir, "newfile.txt").outputStream()
          ContentIO.writeTo(fragment.editor.text, outputStream, true)
          outputStream.close()
          
          activity.runOnUiThread {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_TITLE, "newfile.txt")
            }
            ActivityCompat.startActivityForResult(activity, intent, REQUEST_CODE_CREATE_FILE, null)
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }.start()
    }
    
    private fun saveExistingFile(activity: MainActivity, index: Int) {
      Thread {
        val content = fragments[index].content
        try {
          val outputStream =
            activity.contentResolver.openOutputStream(fragments[index].file.uri, "wt")
          outputStream?.let {
            ContentIO.writeTo(content, it, true)
            it.close()
          }
          activity.runOnUiThread {
            rkUtils.toast(activity, activity.getString(R.string.save))
          }
        } catch (e: Exception) {
          e.printStackTrace()
          activity.runOnUiThread {
            rkUtils.toast(activity, "Error: ${e.message}")
          }
        }
      }.start()
    }
    
    private fun startSettingsActivity(activity: MainActivity): Boolean {
      activity.startActivity(Intent(activity, SettingsMainActivity::class.java))
      return true
    }
    
    private fun handleSaveAll(activity: MainActivity): Boolean {
      for (i in 0 until mTabLayout.tabCount) {
        val tab = mTabLayout.getTabAt(i) ?: continue
        val index = tab.position
        val fragment = fragments[index]
        if (tab.text?.endsWith("*") == true) {
          fragment.isModified = false
          tab.text = tab.text?.dropLast(1)
        }
        saveFile(activity, index)
      }
      After(100) {
        activity.runOnUiThread {
          rkUtils.toast(activity, activity.getString(R.string.saveAll))
        }
      }
      return true
    }
    
    private fun saveFile(activity: MainActivity, index: Int) {
      Thread {
        var outputStream: OutputStream? = null
        if (fragments[index].isNewFile) {
          activity.runOnUiThread {
            rkUtils.toast(
              activity,
              "file : " + fragments[index].fileName + " needs to be saved manually"
            )
          }
          return@Thread
        }
        val content = fragments[index].content
        try {
          outputStream =
            activity.contentResolver.openOutputStream(fragments[index].file.uri, "wt")
          ContentIO.writeTo(content, outputStream!!, true)
        } catch (e: IOException) {
          e.printStackTrace()
        } finally {
          try {
            outputStream?.close()
          } catch (e: IOException) {
            e.printStackTrace()
          }
        }
      }.start()
    }
    
    private fun handleSearch(activity: MainActivity): Boolean {
      val popupView = LayoutInflater.from(activity).inflate(R.layout.popup_search, null)
      val searchBox = popupView.findViewById<TextView>(R.id.searchbox)
      if (!searchText.isNullOrEmpty()) {
        searchBox.text = searchText
      }
      
      MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.search)).setView(popupView)
        .setNegativeButton(activity.getString(R.string.cancel), null).setPositiveButton(activity.getString(R.string.search)) { _, _ ->
          initiateSearch(activity, searchBox, popupView)
        }.show()
      return true
    }
    
    private fun initiateSearch(activity: MainActivity, searchBox: TextView, popupView: View) {
      val undo = StaticData.menu.findItem(R.id.undo)
      val redo = StaticData.menu.findItem(R.id.redo)
      undo.isVisible = false
      redo.isVisible = false
      val editor = fragments[mTabLayout.selectedTabPosition].editor
      val checkBox = popupView.findViewById<CheckBox>(R.id.case_senstive)
      searchText = searchBox.text.toString()
      editor.searcher.search(
        searchText!!,
        SearchOptions(SearchOptions.TYPE_NORMAL, !checkBox.isChecked)
      )
      showSearchMenuItems()
    }
    
    private fun showSearchMenuItems() {
      StaticData.menu.findItem(R.id.search_next).isVisible = true
      StaticData.menu.findItem(R.id.search_previous).isVisible = true
      StaticData.menu.findItem(R.id.search_close).isVisible = true
      StaticData.menu.findItem(R.id.replace).isVisible = true
    }
    
    private fun handleSearchNext(activity: MainActivity): Boolean {
      fragments[mTabLayout.selectedTabPosition].editor.searcher.gotoNext()
      return true
    }
    
    private fun handleSearchPrevious(activity: MainActivity): Boolean {
      fragments[mTabLayout.selectedTabPosition].editor.searcher.gotoPrevious()
      return true
    }
    
    private fun handleSearchClose(activity: MainActivity): Boolean {
      val editor = fragments[mTabLayout.selectedTabPosition].editor
      editor.searcher.stopSearch()
      hideSearchMenuItems()
      searchText = ""
      showUndoRedoMenuItems()
      return true
    }
    
    private fun hideSearchMenuItems() {
      StaticData.menu.findItem(R.id.search_next).isVisible = false
      StaticData.menu.findItem(R.id.search_previous).isVisible = false
      StaticData.menu.findItem(R.id.search_close).isVisible = false
      StaticData.menu.findItem(R.id.replace).isVisible = false
    }
    
    private fun showUndoRedoMenuItems() {
      StaticData.menu.findItem(R.id.undo).isVisible = true
      StaticData.menu.findItem(R.id.redo).isVisible = true
    }
    
    private fun handleReplace(activity: MainActivity): Boolean {
      val popupView = LayoutInflater.from(activity).inflate(R.layout.popup_replace, null)
      MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.replace)).setView(popupView)
        .setNegativeButton(activity.getString(R.string.cancel), null).setPositiveButton(activity.getString(R.string.sora_editor_replaceAll)) { _, _ ->
          replaceAll(activity, popupView)
        }.show()
      return true
    }
    
    private fun replaceAll(activity: MainActivity, popupView: View) {
      val replacementText =
        popupView.findViewById<TextView>(R.id.replace_replacement).text.toString()
      fragments[mTabLayout.selectedTabPosition].editor.searcher.replaceAll(replacementText)
    }
    
    private fun startBatchReplacementActivity(activity: MainActivity): Boolean {
      activity.startActivity(Intent(activity, BatchReplacement::class.java))
      return true
    }
    
    private fun handleUndo(activity: MainActivity): Boolean {
      val fragment = fragments[mTabLayout.selectedTabPosition]
      fragment.Undo()
      updateUndoRedoMenuItems(activity)
      return true
    }
    
    private fun handleRedo(activity: MainActivity): Boolean {
      val fragment = fragments[mTabLayout.selectedTabPosition]
      fragment.Redo()
      updateUndoRedoMenuItems(activity)
      return true
    }
    
    private fun updateUndoRedoMenuItems(activity: MainActivity) {
      val undo = StaticData.menu.findItem(R.id.undo)
      val redo = StaticData.menu.findItem(R.id.redo)
      val editor = fragments[mTabLayout.selectedTabPosition].editor
      redo.isEnabled = editor.canRedo()
      undo.isEnabled = editor.canUndo()
    }
    
    private fun handlePrint(activity: MainActivity): Boolean {
      Printer.print(activity, activity.currentEditor.text.toString())
      return true
    }
    
    private fun handleShare(activity: MainActivity): Boolean {
      rkUtils.shareText(activity, activity.currentEditor.text.toString())
      return true
    }
  }
}
