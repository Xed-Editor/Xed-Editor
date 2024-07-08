package com.rk.xededitor.MainActivity


import android.content.Intent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.app.ActivityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.xededitor.BatchReplacement.BatchReplacement
import com.rk.xededitor.MainActivity.Data.REQUEST_CODE_CREATE_FILE
import com.rk.xededitor.MainActivity.Data.fragments
import com.rk.xededitor.MainActivity.Data.mTabLayout
import com.rk.xededitor.Printer
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsActivity
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import java.io.File
import java.io.IOException
import java.io.OutputStream


class HandleMenuClick() {
  companion object {
    @JvmStatic
    private var SearchText: String? = ""
    
    @JvmStatic
    fun handle(activity: MainActivity,item: MenuItem): Boolean {
      val id = item.itemId
      with(activity) {
        if (id == R.id.action_save) {
          if (Data.fileList.isEmpty()) {
            return true
          }
          val index = mTabLayout.selectedTabPosition
          val fg = fragments[index]
          val tab = mTabLayout.getTabAt(mTabLayout.selectedTabPosition)!!
          
          fg.isModified = false
          tab.setText(fg.fileName)
          
          
          val fragment = fragments.get(mTabLayout.selectedTabPosition)
          if (fragment.isNewFile) {
            Thread {
              try {
                val outputStream = File(externalCacheDir, "newfile.txt").outputStream()
                ContentIO.writeTo(fragment.editor.text, outputStream, true)
                outputStream.close()
                
                runOnUiThread {
                  val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                  intent.setType("text/plain") // Use a generic MIME type
                  intent.putExtra(Intent.EXTRA_TITLE, "newfile.txt") // Default filename
                  ActivityCompat.startActivityForResult(
                    this,
                    intent,
                    REQUEST_CODE_CREATE_FILE,
                    null
                  )
                  
                }
              } catch (e: Exception) {
                e.printStackTrace()
              }
            }.start()
            
          } else {
            Thread {
              val content = Data.contents[mTabLayout.selectedTabPosition]
              try {
                val outputStream = contentResolver.openOutputStream(Data.fileList[index].uri, "wt")
                if (outputStream != null) {
                  ContentIO.writeTo(content, outputStream, true)
                  outputStream.close()
                }
                
                runOnUiThread {
                  rkUtils.toast(activity,
                    "saved!"
                  )
                }
              } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                  rkUtils.toast(activity,
                    """error ${e.message}"""
                  )
                }
              }
            }.start()
            
          }
          
          
          //Content content = fg.editor.getText();
          return true
        } else if (id == R.id.action_settings) {
          startActivity(Intent(activity, SettingsActivity::class.java))
          return true
        } else if (id == R.id.action_all) {
          for (i in 0 until mTabLayout.tabCount) {
            val mtab = mTabLayout.getTabAt(i)
            val index = mtab!!.position
            val fg = fragments[index]
            val name = mtab.text.toString()
            if (name[name.length - 1] == '*') {
              fg.isModified = false
              mtab.setText(name.substring(0, name.length - 1))
            }
            Thread {
              var outputStream: OutputStream? = null
              val content = Data.contents[index]
              try {
                outputStream = contentResolver.openOutputStream(
                  Data.fileList[index].uri, "wt"
                )
                ContentIO.writeTo(content, outputStream!!, true)
              } catch (e: IOException) {
                e.printStackTrace()
              } finally {
                try {
                  outputStream!!.close()
                } catch (e: IOException) {
                  e.printStackTrace()
                }
              }
            }.start()
          }
          rkUtils.toast(activity,"saved all")
          return true
        } else if (id == R.id.search) {
          val popuop_view = LayoutInflater.from(this).inflate(R.layout.popup_search, null)
          val searchBox = popuop_view.findViewById<TextView>(R.id.searchbox)
          if (!SearchText?.isEmpty()!!) {
            searchBox.text = SearchText
          }
          MaterialAlertDialogBuilder(this).setTitle("Search").setView(popuop_view)
            .setNegativeButton("Cancel", null).setPositiveButton(
              "Search"
            ) { dialogInterface, i ->
              val undo = Data.menu.findItem(R.id.undo)
              val redo = Data.menu.findItem(R.id.redo)
              undo.setVisible(false)
              redo.setVisible(false)
              val editor = fragments[mTabLayout.selectedTabPosition].getEditor()
              val checkBox = popuop_view.findViewById<CheckBox>(R.id.case_senstive)
              SearchText = searchBox.getText().toString()
              editor.searcher.search(
                SearchText!!, SearchOptions(SearchOptions.TYPE_NORMAL, !checkBox.isChecked)
              )
              Data.menu.findItem(R.id.search_next).setVisible(true)
              Data.menu.findItem(R.id.search_previous).setVisible(true)
              Data.menu.findItem(R.id.search_close).setVisible(true)
              Data.menu.findItem(R.id.replace).setVisible(true)
            }.show()
          return true
        } else if (id == R.id.search_next) {
          val editor = fragments[mTabLayout.selectedTabPosition].getEditor()
          editor.searcher.gotoPrevious()
          return true
        } else if (id == R.id.search_previous) {
          val editor = fragments[mTabLayout.selectedTabPosition].getEditor()
          editor.searcher.gotoNext()
          return true
        } else if (id == R.id.search_close) {
          val editor = fragments[mTabLayout.selectedTabPosition].getEditor()
          editor.searcher.stopSearch()
          Data.menu.findItem(R.id.search_next).setVisible(false)
          Data.menu.findItem(R.id.search_previous).setVisible(false)
          Data.menu.findItem(R.id.search_close).setVisible(false)
          Data.menu.findItem(R.id.replace).setVisible(false)
          SearchText = ""
          val undo = Data.menu.findItem(R.id.undo)
          val redo = Data.menu.findItem(R.id.redo)
          undo.setVisible(true)
          redo.setVisible(true)
          return true
        } else if (id == R.id.replace) {
          val popuop_view = LayoutInflater.from(this).inflate(R.layout.popup_replace, null)
          MaterialAlertDialogBuilder(this).setTitle("Replace").setView(popuop_view)
            .setNegativeButton("Cancel", null).setPositiveButton(
              "Replace All"
            ) { dialog, which ->
              fragments[mTabLayout.selectedTabPosition].getEditor().searcher.replaceAll(
                (popuop_view.findViewById<View>(R.id.replace_replacement) as TextView).getText()
                  .toString()
              )
            }.show()
        } else if (id == R.id.batchrep) {
          val intent = Intent(this, BatchReplacement::class.java)
          startActivity(intent)
        } else if (id == R.id.undo) {
          fragments[mTabLayout.selectedTabPosition].Undo()
          val undo = Data.menu.findItem(R.id.undo)
          val redo = Data.menu.findItem(R.id.redo)
          val editor = fragments[mTabLayout.selectedTabPosition].getEditor()
          redo.setEnabled(editor.canRedo())
          undo.setEnabled(editor.canUndo())
        } else if (id == R.id.redo) {
          fragments[mTabLayout.selectedTabPosition].Redo()
          val undo = Data.menu.findItem(R.id.undo)
          val redo = Data.menu.findItem(R.id.redo)
          val editor = fragments[mTabLayout.selectedTabPosition].getEditor()
          redo.setEnabled(editor.canRedo())
          undo.setEnabled(editor.canUndo())
        } else if (id == R.id.action_print) {
          Printer.print(this, mAdapter.getCurrentEditor().text.toString())
        }else if(id == R.id.share){
            rkUtils.shareText(this, getCurrentEditor().text.toString());
        } else {
          return true
        }
      }
      
      return true
    }
  }
}