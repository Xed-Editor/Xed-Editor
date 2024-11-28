package com.rk.crashhandler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.rk.crashhandler.databinding.ActivityCrashBinding
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


@Suppress("NOTHING_TO_INLINE")
class CrashActivity : AppCompatActivity() {
    private lateinit var binding:ActivityCrashBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.heading.text = "Karbon has crashed"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.crash_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        when (id) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }

            R.id.copy_error -> {
            
            }

            R.id.report_issue -> {
                val browserIntent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://github.com/Xed-Editor/Xed-Editor/issues/new?title=Crash%20Report&body=" +
                                URLEncoder.encode(
                                    "``` ```",
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
