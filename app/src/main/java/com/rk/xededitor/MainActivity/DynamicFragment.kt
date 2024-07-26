package com.rk.xededitor.MainActivity

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.rk.xededitor.After
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils
import com.rk.xededitor.runOnUi
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class DynamicFragment : Fragment {
  lateinit var fileName: String
  var file: File? = null
  private var ctx: Context? = null
  lateinit var editor: CodeEditor
  var editorx: CodeEditor? = null
  var content: Content? = null
  var isModified: Boolean = false
  var undo: MenuItem? = null
  var redo: MenuItem? = null
  
  
  constructor() {
    After(100) {
      MainActivity.activity.runOnUiThread {
        val fragmentManager = MainActivity.activity.supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.remove(this)
        fragmentTransaction.commitNowAllowingStateLoss()
      }
    }
  }
  
  constructor(file: File, ctx: Context) {
    this.fileName = file.name
    this.ctx = ctx
    this.file = file
    editor = CodeEditor(ctx)
    editorx = editor
    
    
    when(fileName.substringAfterLast('.', "")){
      "java" -> {setLanguage("source.java")}
      "html" -> {setLanguage("text.html.basic")}
      "kt" -> {setLanguage("source.kotlin")}
      "py" -> {setLanguage("source.python")}
      "xml" -> {setLanguage("text.xml")}
      "js" -> {setLanguage("source.js")}
      "md" -> {setLanguage("text.html.markdown")}
    }
    
    
    
    
    
    
    if (SettingsData.isDarkMode(ctx)) {
      ensureTextmateTheme()
    } else {
      Thread { this.ensureTextmateTheme() }.start()
    }
    
    val wordwrap = SettingsData.getBoolean(ctx, "wordwrap", false)
    
    
    
    Thread {
      try {
        val inputStream: InputStream = FileInputStream(file)
        content = ContentIO.createFrom(inputStream)
        inputStream.close()
        runOnUi { editor.setText(content) }
        if (wordwrap) {
          val length = content.toString().length
          if (length > 700 && content.toString().split("\\R".toRegex())
              .dropLastWhile { it.isEmpty() }.toTypedArray().size < 100
          ) {
            runOnUi {
              rkUtils.toast(
                ctx, resources.getString(R.string.ww_wait)
              )
            }
          }
          if (length > 1500) {
            runOnUi {
              Toast.makeText(
                ctx, resources.getString(R.string.ww_wait), Toast.LENGTH_LONG
              ).show()
            }
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
      setListener()
    }.start()
    
    
    editor.typefaceText = Typeface.createFromAsset(ctx.assets, "JetBrainsMono-Regular.ttf")
    editor.setTextSize(14f)
    editor.isWordwrap = wordwrap
    
    undo = StaticData.menu.findItem(R.id.undo)
    redo = StaticData.menu.findItem(R.id.redo)
  }
  
  private fun setListener() {
    editor.subscribeAlways(
      ContentChangeEvent::class.java
    ) { event: ContentChangeEvent? ->
      updateUndoRedo()
      val tab = StaticData.mTabLayout.getTabAt(StaticData.fragments.indexOf(this))
      if (isModified) {
        tab!!.setText("$fileName*")
      }
      isModified = true
    }
  }
  
  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View? {
    return editorx
  }
  
  fun updateUndoRedo() {
    if (undo != null && redo != null) {
      redo!!.setEnabled(editor.canRedo())
      undo!!.setEnabled(editor.canUndo())
    }
  }
  
  @JvmOverloads
  fun releaseEditor(removeCoontent: Boolean = false) {
    editor.release()
    content = null
  }
  
  fun Undo() {
    if (editor.canUndo()) {
      editor.undo()
    }
  }
  
  fun Redo() {
    if (editor.canRedo()) {
      editor.redo()
    }
  }
  
  private fun ensureTextmateTheme() {
    var editorColorScheme = editor.colorScheme
    val themeRegistry = ThemeRegistry.getInstance()
    
    val darkMode = SettingsData.isDarkMode(ctx)
    try {
      if (darkMode) {
        val path = if (SettingsData.isOled(ctx)) {
          ctx!!.getExternalFilesDir(null)!!.absolutePath + "/unzip/textmate/black/darcula.json"
        } else {
          ctx!!.getExternalFilesDir(null)!!.absolutePath + "/unzip/textmate/darcula.json"
        }
        if (!File(path).exists()) {
          runOnUi {
            rkUtils.toast(
              ctx, resources.getString(R.string.theme_not_found_err)
            )
          }
        }
        
        themeRegistry.loadTheme(
          ThemeModel(
            IThemeSource.fromInputStream(
              FileProviderRegistry.getInstance().tryGetInputStream(path), path, null
            ), "darcula"
          )
        )
        editorColorScheme = TextMateColorScheme.create(themeRegistry)
        if (SettingsData.isOled(ctx)) {
          editorColorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.BLACK)
        }
      } else {
        val path =
          ctx!!.getExternalFilesDir(null)!!.absolutePath + "/unzip/textmate/quietlight.json"
        if (!File(path).exists()) {
          runOnUi {
            rkUtils.toast(
              ctx, resources.getString(R.string.theme_not_found_err)
            )
          }
        }
        themeRegistry.loadTheme(
          ThemeModel(
            IThemeSource.fromInputStream(
              FileProviderRegistry.getInstance().tryGetInputStream(path), path, null
            ), "quitelight"
          )
        )
        editorColorScheme = TextMateColorScheme.create(themeRegistry)
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    
    if (darkMode) {
      val pref = ctx!!.applicationContext.getSharedPreferences("MyPref", 0)
      themeRegistry.setTheme("darcula")
    } else {
      themeRegistry.setTheme("quietlight")
    }
    synchronized(editor) {
      editor.colorScheme = editorColorScheme
    }
  }
  
  fun setLanguage(languageScopeName:String){
    FileProviderRegistry.getInstance().addFileProvider(
      AssetsFileResolver(
        ctx?.applicationContext?.assets
      )
    )
    
    GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    
    val language = TextMateLanguage.create(
      languageScopeName, true /* true for enabling auto-completion */
    )
    editor.setEditorLanguage(language as Language)
  }
}