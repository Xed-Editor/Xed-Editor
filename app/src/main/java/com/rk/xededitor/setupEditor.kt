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

class setupEditor(val editor: CodeEditor, private val ctx: Context) {


  fun setupLanguage(fileName: String) {
    when (fileName.substringAfterLast('.', "")) {
      "java" -> {
        setLanguage("source.java")
      }

      "html" -> {
        setLanguage("text.html.basic")
      }

      "kt" -> {
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

      "kts" -> {
        setLanguage("source.kotlin")
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
    }
  }

  private fun setLanguage(languageScopeName: String) {
    FileProviderRegistry.getInstance().addFileProvider(
      AssetsFileResolver(
        ctx.applicationContext?.assets
      )
    )

    GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")

    val language = TextMateLanguage.create(
      languageScopeName, true /* true for enabling auto-completion */
    )
    editor.setEditorLanguage(language as Language)
  }


  fun ensureTextmateTheme() {
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
          rkUtils.runOnUiThread {
            rkUtils.toast(
              ctx, ctx.resources.getString(R.string.theme_not_found_err)
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
          rkUtils.runOnUiThread {
            rkUtils.toast(
              ctx, ctx.resources.getString(R.string.theme_not_found_err)
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
}