package com.rk.xededitor

import android.content.Context
import android.graphics.Color
import com.rk.xededitor.Settings.SettingsData
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.File

class SetupEditor(val editor: CodeEditor, private val ctx: Context) {

  fun setupLanguage(fileName: String) {
    when (fileName.substringAfterLast('.', "")) {
      "java","bsh" -> {
        setLanguage("source.java")
      }

      "html" -> {
        setLanguage("text.html.basic")
      }

      "kt","kts" -> {
        setLanguage("source.kotlin")
      }

      "py" -> {
        setLanguage("source.python")
      }

      "xml" -> {
        setLanguage("text.xml")
      }

      "js" -> {
        setLanguage("source.js")
      }

      "md" -> {
        setLanguage("text.html.markdown")
      }

      "c" -> {
        setLanguage("source.c")
      }

      "cpp", "h" -> {
        setLanguage("source.cpp")
      }

      "json" -> {
        setLanguage("source.json")
      }

      "css" -> {
        setLanguage("source.css")
      }

      "cs" -> {
        setLanguage("source.cs")
      }
    }
  }

  companion object{
    fun init(context: Context){
      initGrammarRegistry(context)
      initTextMateTheme(context)
    }
    private fun initGrammarRegistry(context: Context){
      FileProviderRegistry.getInstance().addFileProvider(
        AssetsFileResolver(
          context.applicationContext?.assets
        )
      )
      
      GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
      
    }
    private fun initTextMateTheme(context: Context){
      Assets.verify(context)
      val darkMode = SettingsData.isDarkMode(context)
      val themeRegistry = ThemeRegistry.getInstance()
      try {
        if (darkMode) {
          val path = if (SettingsData.isOled()) {
            File(context.filesDir,"unzip/textmate/black/darcula.json").absolutePath
          } else {
            File(context.filesDir,"unzip/textmate/darcula.json").absolutePath
          }
          if (!File(path).exists()) {
            rkUtils.runOnUiThread {
              rkUtils.toast(
                context, context.resources.getString(R.string.theme_not_found_err)
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
          
        } else {
          val path = File(context.filesDir,"unzip/textmate/quietlight.json").absolutePath
          if (!File(path).exists()) {
            rkUtils.runOnUiThread {
              rkUtils.toast(
                context, context.resources.getString(R.string.theme_not_found_err)
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
          
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  
  
 
  
  private fun setLanguage(languageScopeName: String) {
    val language = TextMateLanguage.create(
      languageScopeName, true /* true for enabling auto-completion */
    )
    editor.setEditorLanguage(language as Language)
  }


  
  fun ensureTextmateTheme() {
    val themeRegistry = ThemeRegistry.getInstance()
    val editorColorScheme: EditorColorScheme = TextMateColorScheme.create(themeRegistry)
    
    if (SettingsData.isDarkMode(ctx)) {
      if (SettingsData.isOled()) {
        editorColorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.BLACK)
      }
      themeRegistry.setTheme("darcula")
    } else {
      themeRegistry.setTheme("quietlight")
    }
    synchronized(editor) {
      editor.colorScheme = editorColorScheme
    }
  }
}