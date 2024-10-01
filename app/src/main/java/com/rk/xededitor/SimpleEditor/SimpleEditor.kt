package com.rk.xededitor.SimpleEditor

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.rk.libcommons.After
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData.getBoolean
import com.rk.xededitor.Settings.SettingsData.getString
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.toast
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class SimpleEditor : BaseActivity() {
  var undo: MenuItem? = null
  var redo: MenuItem? = null
  private var content: Content? = null
  private var uri: Uri? = null
  var menu: Menu? = null
  var SearchText = ""

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_simple_editor)
    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    editor = findViewById(R.id.editor)
    supportActionBar!!.setDisplayShowTitleEnabled(false)
    supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    SetupEditor.init(this@SimpleEditor)
    SetupEditor(editor!!, this@SimpleEditor).ensureTextmateTheme(this)


    editor!!.setTypefaceText(
      Typeface.createFromAsset(
        assets, "JetBrainsMono-Regular.ttf"
      )
    )
    editor!!.setTextSize(getString(Keys.TEXT_SIZE, "14").toFloat())
    val wordwrap = getBoolean(Keys.WORD_WRAP_ENABLED, false)
    editor!!.isWordwrap = wordwrap
    editor!!.getComponent(EditorAutoCompletion::class.java).isEnabled = true

    editor!!.subscribeAlways(ContentChangeEvent::class.java) {
      if (redo != null) {
        redo!!.setEnabled(editor!!.canRedo())
        undo!!.setEnabled(editor!!.canUndo())
      }
    }

    handleIntent(intent)

  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    // Handle action bar item clicks here
    val id = item.itemId
    HandleMenuItemClick.handle(this, id)
    return super.onOptionsItemSelected(item)
  }

  @SuppressLint("RestrictedApi")
  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)
    this.menu = menu

    if (menu is MenuBuilder) {
      menu.setOptionalIconsVisible(true)
    }

    undo = menu.findItem(R.id.undo)
    redo = menu.findItem(R.id.redo)


    After(200) {
      runOnUiThread {
        undo?.apply {
          isVisible = true
          setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        redo?.apply {
          isVisible = true
          setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        menu.findItem(R.id.action_settings).apply {
          setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }

        menu.findItem(R.id.action_save).apply {
          isVisible = true

        }

        menu.findItem(R.id.action_print).apply {
          isVisible = true
        }

        menu.findItem(R.id.share).apply {
          isVisible = true
        }

        menu.findItem(R.id.insertdate).apply {
          isVisible = true
        }

      }
    }

    return true
  }

  private fun handleIntent(intent: Intent?) {
    if (intent != null && (Intent.ACTION_VIEW == intent.action || Intent.ACTION_EDIT == intent.action)) {
      uri = intent.data

      if (uri != null) {
        val mimeType = contentResolver.getType(uri!!)
        if (mimeType != null) {
          if (mimeType.isEmpty() || mimeType.contains("directory")) {
            toast(resources.getString(R.string.unsupported_contnt))
            finish()
          }
        }

        var displayName: String? = null
        try {
          contentResolver.query(uri!!, null, null, null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
              val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
              if (nameIndex >= 0) {
                displayName = cursor.getString(nameIndex)
              }
            }
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }

        SetupEditor(editor!!, this@SimpleEditor).setupLanguage(
          displayName!!
        )


        if (displayName!!.length > 13) {
          displayName = displayName!!.substring(0, 10) + "..."
        }
        supportActionBar!!.title = displayName


        try {
          val inputStream = contentResolver.openInputStream(
            uri!!
          )
          if (inputStream != null) {
            content = ContentIO.createFrom(inputStream)
            editor!!.setText(content)
            inputStream.close()
          }
        } catch (e: IOException) {
          e.printStackTrace()
        }
      }
    }
  }

  fun save() {
    lifecycleScope.launch(Dispatchers.IO) {
      var s: String
      try {
        val outputStream = contentResolver.openOutputStream(uri!!, "wt")
        if (outputStream != null) {
          ContentIO.writeTo(editor!!.text, outputStream, true)
          s = rkUtils.getString(R.string.saved)
        } else {
          s = rkUtils.getString(R.string.is_null)
        }
      } catch (e: IOException) {
        e.printStackTrace()
        s = "${rkUtils.getString(R.string.u_err)} \n$e"
      }

      withContext(Dispatchers.Main) {
        toast(s)
      }
    }
  }

  companion object {
    var editor: CodeEditor? = null
  }
}
