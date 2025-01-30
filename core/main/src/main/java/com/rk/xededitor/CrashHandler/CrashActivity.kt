package com.rk.xededitor.CrashHandler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.rk.libcommons.editor.KarbonEditor
import com.rk.libcommons.editor.SetupEditor
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.R
import io.github.rosemoe.sora.widget.CodeEditor
import java.lang.System
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.system.exitProcess

@Suppress("NOTHING_TO_INLINE")
class CrashActivity : AppCompatActivity() {
    private lateinit var editor: KarbonEditor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching{
            enableEdgeToEdge()
            setContentView(R.layout.activity_error)
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
            editor = findViewById(R.id.error_editor)
            editor.setTextSize(10f)

            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)


            val sb = StringBuilder()

            sb.append("Fatal Crash occurred on Thread named '").append(intent.getStringExtra("thread")).append("\n\n")
            sb.append("App Version : ").append(versionName).append("\n")
            sb.append("Version Code : ").append(versionCode).append("\n")
            sb.append("Commit hash : ").append(BuildConfig.GIT_COMMIT_HASH.substring(0,8)).append("\n")
            sb.append("Commit date : ").append(BuildConfig.GIT_COMMIT_DATE).append("\n")
            sb.append("Unix Time : ").append(System.currentTimeMillis()).append("\n")
            sb.append("LocalTime : ").append(SimpleDateFormat.getDateTimeInstance().format(Date(System.currentTimeMillis()))).append("\n")
            sb.append("Android Version : ").append(Build.VERSION.RELEASE).append("\n")
            sb.append("SDK Version : ").append(Build.VERSION.SDK_INT).append("\n")
            sb.append("Brand : ").append(Build.BRAND).append("\n")
            sb.append("Manufacturer : ").append(Build.MANUFACTURER).append("\n\n")

            sb.append("Error Message : ").append(intent.getStringExtra("msg")).append("\n")
            sb.append("Error Cause : ").append(intent.getStringExtra("error_cause")).append("\n")
            sb.append("Error StackTrace : \n").append(intent.getStringExtra("stacktrace"))


            editor.setText(sb.toString())
            editor.editable = false

            runCatching { SetupEditor(editor,this,lifecycleScope) }
            editor.isWordwrap = false
        }.onFailure{
            it.printStackTrace()
            runCatching { finishAffinity() }
            exitProcess(1)
        }

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
                onBackPressedDispatcher.onBackPressed()
                return true
            }

            R.id.copy_error -> {
                copyToClipboard(this, editor.text.toString())
                Toast.makeText(this,"Copied",android.widget.Toast.LENGTH_SHORT).show()
            }

            R.id.report_issue -> {
                val browserIntent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://github.com/Xed-Editor/Xed-Editor/issues/new?title=Crash%20Report&body=" +
                                URLEncoder.encode(
                                    "``` \n${editor.text}\n ```",
                                    StandardCharsets.UTF_8.toString(),
                                )
                        ),
                    )
                startActivity(browserIntent)
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private inline fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("label", text)
        clipboard.setPrimaryClip(clip)
    }
}
