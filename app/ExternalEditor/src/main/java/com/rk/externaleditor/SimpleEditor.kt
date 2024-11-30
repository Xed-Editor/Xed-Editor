package com.rk.externaleditor

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.rk.libcommons.After
import com.rk.libcommons.PathUtils.toPath
import com.rk.runner.Runner
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rk.libcommons.KarbonEditor
import com.rk.libcommons.SetupEditor

class SimpleEditor : AppCompatActivity() {
    var undo: MenuItem? = null
    var redo: MenuItem? = null
    private var content: Content? = null
    private var uri: Uri? = null
    var menu: Menu? = null
    var SearchText = ""
    var editor: KarbonEditor? = null
    lateinit var setupEditor: SetupEditor
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null) {
            editor?.let { KeyEventHandler.onKeyEvent(event, it, this) }
        }
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_editor)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        editor = findViewById(R.id.editor)
        supportActionBar!!.setDisplayShowTitleEnabled(false)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        editor!!.subscribeAlways(ContentChangeEvent::class.java) {
            if (redo != null) {
                redo!!.setEnabled(editor!!.canRedo())
                undo!!.setEnabled(editor!!.canUndo())
            }
        }
        setupEditor = SetupEditor(editor!!,this,lifecycleScope)

        handleIntent(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
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

                menu.findItem(R.id.action_settings).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                menu.findItem(R.id.action_save).isVisible = true
                menu.findItem(R.id.action_print).isVisible = true
                menu.findItem(R.id.share).isVisible = true
                menu.findItem(R.id.search).isVisible = true
                menu.findItem(R.id.share).isVisible = true
                menu.findItem(R.id.suggestions).isVisible = true
            }
        }

        return true
    }

    private fun handleIntent(intent: Intent?) {
        if (
            intent != null &&
                (Intent.ACTION_VIEW == intent.action || Intent.ACTION_EDIT == intent.action)
        ) {
            uri = intent.data
            
            val path = uri!!.toPath()
            File(path).let {
                if (it.exists() and Runner.isRunnable(it)){
                    lifecycleScope.launch(Dispatchers.Default){
                        while (menu == null){
                            delay(100)
                        }
                        withContext(Dispatchers.Main){
                            menu!!.findItem(R.id.run).isVisible = true
                        }
                    }
                    
                }
            }

            if (uri != null) {
                val mimeType = contentResolver.getType(uri!!)
                if (mimeType != null) {
                    if (mimeType.isEmpty() || mimeType.contains("directory")) {
                        Toast.makeText(this,"R.string.unsupported_contnt",Toast.LENGTH_SHORT).show()
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
                
                lifecycleScope.launch { setupEditor?.setupLanguage(displayName!!)
                
                }
                

                if (displayName!!.length > 13) {
                    displayName = displayName!!.substring(0, 10) + "..."
                }
                supportActionBar!!.title = displayName

                try {
                    val inputStream = contentResolver.openInputStream(uri!!)
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
                ContentIO.writeTo(editor!!.text, outputStream!!, true)
                s = "Saved"
            } catch (e: Exception) {
                e.printStackTrace()
                s = e.message.toString()
            }

            withContext(Dispatchers.Main) { Toast.makeText(this@SimpleEditor,s,Toast.LENGTH_SHORT).show() }
        }
    }
}
