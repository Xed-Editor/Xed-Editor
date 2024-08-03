package com.rk.xededitor.CrashHandler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date


class CrashActivity : AppCompatActivity() {
  private lateinit var error_editor: CodeEditor


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_error)
    if (SettingsData.isDarkMode(this) && SettingsData.isOled(this)) {
      findViewById<Toolbar>(R.id.toolbar).setBackgroundColor(Color.BLACK)
    }

    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
      insets
    }
    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    toolbar.setTitle("Error")
    setSupportActionBar(toolbar)
    supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    supportActionBar!!.setDisplayShowTitleEnabled(true)
    error_editor = findViewById(R.id.error_editor)
    ensureTextmateTheme()




    try {
      val sb = StringBuilder()
      sb.append("Fatal Crash occurred on Thread named '").append(intent.getStringExtra("thread"))
        .append("'\nUnix Time : ").append(System.currentTimeMillis()).append("\n")
      sb.append("LocalTime : ")
        .append(SimpleDateFormat.getDateTimeInstance().format(Date(System.currentTimeMillis())))
        .append("\n\n")
      sb.append(intent.getStringExtra("info")).append("\n\n")
      sb.append("Error Message : ").append(intent.getStringExtra("msg")).append("\n")
      sb.append("Error Cause : ").append(intent.getStringExtra("error_cause")).append("\n")
      sb.append("Error StackTrace : \n\n").append(intent.getStringExtra("stacktrace"))
      error_editor.setText(sb.toString())
    } catch (e: Exception) {
      e.printStackTrace()
    }
    error_editor.editable = false
    if (!SettingsData.isDarkMode(this)) {
      //light mode
      window.navigationBarColor = Color.parseColor("#FEF7FF")
      val decorView = window.decorView
      var flags = decorView.systemUiVisibility
      flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
      decorView.systemUiVisibility = flags
    }
    if (SettingsData.isDarkMode(this) && SettingsData.isOled(this)) {
      val window = window
      window.navigationBarColor = Color.BLACK
      window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
      window.statusBarColor = Color.BLACK
    }
  }


  private fun ensureTextmateTheme() {
    var editorColorScheme: EditorColorScheme = error_editor.colorScheme
    val themeRegistry = ThemeRegistry.getInstance()
    val darkMode = SettingsData.isDarkMode(this)
    try {
      if (darkMode) {
        val path: String
        path = if (SettingsData.isOled(this)) {
          this.getExternalFilesDir(null)!!.absolutePath + "/unzip/textmate/black/darcula.json"
        } else {
          this.getExternalFilesDir(null)!!.absolutePath + "/unzip/textmate/darcula.json"
        }
        if (!File(path).exists()) {
          runOnUiThread {
            rkUtils.toast(
              this,
              getResources().getString(R.string.theme_not_found_err)
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
        if (SettingsData.isOled(this)) {
          editorColorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.BLACK)
        }
      } else {
        val path: String =
          this.getExternalFilesDir(null)!!.absolutePath + "/unzip/textmate/quietlight.json"
        if (!File(path).exists()) {
          runOnUiThread {
            rkUtils.toast(
              this,
              getResources().getString(R.string.theme_not_found_err)
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
    } catch (e: java.lang.Exception) {
      e.printStackTrace()
    }
    if (darkMode) {
      val pref: SharedPreferences = this.applicationContext.getSharedPreferences("MyPref", 0)
      themeRegistry.setTheme("darcula")
    } else {
      themeRegistry.setTheme("quietlight")
    }
    error_editor.setColorScheme(editorColorScheme)
  }


  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.crash_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    // Handle action bar item clicks here
    val id = item.itemId

    when (id) {
      android.R.id.home -> {
        onBackPressed()
        return true
      }

      R.id.copy_error -> {
        copyToClipboard(this, error_editor.text.toString())
      }

      R.id.report_issue -> {
        val browserIntent = Intent(
          Intent.ACTION_VIEW,
          Uri.parse(
            "https://github.com/RohitKushvaha01/Xed-Editor/issues/new?title=Crash%20Report&body=" + URLEncoder.encode(
              error_editor.text.toString(),
              StandardCharsets.UTF_8.toString()
            )
          )
        )
        startActivity(browserIntent)
      }
    }


    return super.onOptionsItemSelected(item)
  }

  fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("label", text)
    clipboard.setPrimaryClip(clip)
  }

}