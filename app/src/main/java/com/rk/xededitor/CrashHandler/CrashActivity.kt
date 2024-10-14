package com.rk.xededitor.CrashHandler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rk.xededitor.R
import com.rk.xededitor.SetupEditor
import io.github.rosemoe.sora.widget.CodeEditor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.system.exitProcess

@Suppress("NOTHING_TO_INLINE")
class CrashActivity : AppCompatActivity() {
    private lateinit var editor: CodeEditor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
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
            editor.setTextSize(11f)
            SetupEditor(editor, this).ensureTextmateTheme(this)

            try {
                val sb = StringBuilder()
                sb.append("Fatal Crash occurred on Thread named '")
                    .append(intent.getStringExtra("thread"))
                    .append("'\nUnix Time : ")
                    .append(System.currentTimeMillis())
                    .append("\n")
                sb.append("LocalTime : ")
                    .append(
                        SimpleDateFormat.getDateTimeInstance()
                            .format(Date(System.currentTimeMillis()))
                    )
                    .append("\n\n")
                sb.append(intent.getStringExtra("info")).append("\n\n")
                sb.append("Error Message : ").append(intent.getStringExtra("msg")).append("\n")
                sb.append("Error Cause : ")
                    .append(intent.getStringExtra("error_cause"))
                    .append("\n")
                sb.append("Error StackTrace : \n\n").append(intent.getStringExtra("stacktrace"))
                editor.setText(sb.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
            editor.editable = false
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                finishAffinity()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                onBackPressed()
                return true
            }

            R.id.copy_error -> {
                copyToClipboard(this, editor.text.toString())
            }

            R.id.report_issue -> {
                val browserIntent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://github.com/RohitKushvaha01/Xed-Editor/issues/new?title=Crash%20Report&body=" +
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
