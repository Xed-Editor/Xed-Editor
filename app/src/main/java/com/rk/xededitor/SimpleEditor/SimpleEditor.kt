package com.rk.xededitor.SimpleEditor

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.Decompress
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.BatchReplacement.BatchReplacement
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.Settings.SettingsData.getBoolean
import com.rk.xededitor.Settings.SettingsData.getString
import com.rk.xededitor.Settings.SettingsData.isDarkMode
import com.rk.xededitor.Settings.SettingsData.isOled
import com.rk.xededitor.Settings.SettingsMainActivity
import com.rk.xededitor.rkUtils.toast
import com.rk.xededitor.setupEditor
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import java.io.File
import java.io.IOException

class SimpleEditor : BaseActivity() {
    private var undo: MenuItem? = null
    private var redo: MenuItem? = null
    private var content: Content? = null
    private var uri: Uri? = null
    private var menu: Menu? = null
    private var SearchText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_editor)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        editor = findViewById(R.id.editor)
        supportActionBar!!.setDisplayShowTitleEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)


        if (!isDarkMode(this)) {
            //light mode
            window.navigationBarColor = Color.parseColor("#FEF7FF")
            val decorView = window.decorView
            var flags = decorView.systemUiVisibility
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            decorView.systemUiVisibility = flags
        } else if (isOled()) {
            toolbar.setBackgroundColor(Color.BLACK)
            val window = window
            window.navigationBarColor = Color.BLACK
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.BLACK
        }
        if (!File(getExternalFilesDir(null).toString() + "/unzip").exists()) {
            Thread {
                try {
                    Decompress.unzipFromAssets(
                        this, "files.zip", getExternalFilesDir(null).toString() + "/unzip"
                    )
                    File(getExternalFilesDir(null).toString() + "files").delete()
                    File(getExternalFilesDir(null).toString() + "files.zip").delete()
                    File(getExternalFilesDir(null).toString() + "textmate").delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }

        editor!!.setTypefaceText(
            Typeface.createFromAsset(
                assets, "JetBrainsMono-Regular.ttf"
            )
        )
        editor!!.setTextSize(getString(SettingsData.Keys.TEXT_SIZE, "14").toFloat())
        val wordwrap = getBoolean(SettingsData.Keys.WORD_WRAP_ENABLED, false)
        editor!!.isWordwrap = wordwrap

        Thread { setupEditor(editor!!, this@SimpleEditor).ensureTextmateTheme() }.start()


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
        when (id) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }

            R.id.action_settings -> {
                startActivity(Intent(this, SettingsMainActivity::class.java))
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
                    .setNegativeButton("Cancel", null).setPositiveButton("Search") { _, _ ->
                        val checkBox = popuopView.findViewById<CheckBox>(R.id.case_senstive)
                        SearchText = searchBox.text.toString()
                        editor!!.searcher.search(
                            SearchText,
                            SearchOptions(SearchOptions.TYPE_NORMAL, !checkBox.isChecked)
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
                    .setNegativeButton("Cancel", null).setPositiveButton("Replace All") { _, _ ->
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

            R.id.batchrep -> {
                val intent = Intent(this, BatchReplacement::class.java)
                intent.putExtra("isExt", true)
                startActivity(intent)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.simple_mode_menu, menu)
        this.menu = menu
        undo = menu.findItem(R.id.undo)
        redo = menu.findItem(R.id.redo)
        return true
    }

    private fun handleIntent(intent: Intent?) {
        if (intent != null && (Intent.ACTION_VIEW == intent.action || Intent.ACTION_EDIT == intent.action)) {
            uri = intent.data

            if (uri != null) {
                val mimeType = contentResolver.getType(uri!!)
                if (mimeType != null) {
                    if (mimeType.isEmpty() || mimeType.contains("directory")) {
                        toast(this, resources.getString(R.string.unsupported_contnt))
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

                setupEditor(editor!!, this@SimpleEditor).setupLanguage(
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
                        if (content != null) {
                            editor!!.setText(content) // Ensure content.toString() is what you intend to set
                        } else {
                            toast(this, resources.getString(R.string.null_contnt))
                        }
                        inputStream.close()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun save() {
        Thread {
            var s: String
            try {
                val outputStream = contentResolver.openOutputStream(uri!!, "wt")
                if (outputStream != null) {
                    ContentIO.writeTo(content!!, outputStream, true)
                    s = "saved"
                } else {
                    s = "InputStream is null"
                }
            } catch (e: IOException) {
                e.printStackTrace()
                s = "Unknown Error \n$e"
            }
            val toast = s
            this@SimpleEditor.runOnUiThread {
                toast(this@SimpleEditor, toast)
            }
        }.start()
    }

    companion object {
        var editor: CodeEditor? = null
    }
}
