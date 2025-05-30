package com.rk.crashhandler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.rk.libcommons.editor.KarbonEditor
import com.rk.libcommons.editor.SetupEditor
import com.rk.libcommons.isFdroid
import com.rk.libcommons.origin
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.R
import java.lang.System
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.system.exitProcess

class CrashActivity : AppCompatActivity() {
    private lateinit var editor: KarbonEditor

    companion object {
        fun Context.isModified(): Boolean {
            if (BuildConfig.DEBUG) {
                return false
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            }

            if (signatures == null) {
                return true
            }

            for (signature in signatures) {
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(signature.toByteArray().inputStream()) as X509Certificate
                val sha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                val hex = sha256.joinToString(":") { "%02X".format(it) }

                if (hex.equals(
                        assets.open("hash").bufferedReader().use { it.readText() },
                        ignoreCase = true
                    )
                ) {
                    return false
                }
            }
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            enableEdgeToEdge()
            setContentView(R.layout.activity_error)
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            val toolbar = findViewById<Toolbar>(R.id.toolbar)
            toolbar.setTitle(strings.err.getString())
            setSupportActionBar(toolbar)
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setDisplayShowTitleEnabled(true)
            editor = findViewById(R.id.error_editor)
            editor.setTextSize(10f)

            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)


            val sb = StringBuilder()

            sb.append("Unexpected Crash occurred").appendLine().appendLine()

            sb.append("Thread : ").append(intent.getStringExtra("thread")).appendLine()
            sb.append("App Version : ").append(versionName).appendLine()
            sb.append("Version Code : ").append(versionCode).appendLine()
            sb.append("Modified : ").append(isModified()).appendLine()
            sb.append("Commit hash : ").append(BuildConfig.GIT_COMMIT_HASH.substring(0, 8))
                .appendLine()
            sb.append("Commit date : ").append(BuildConfig.GIT_COMMIT_DATE).appendLine()
            sb.append("Origin : ").append(origin()).appendLine()
            sb.append("Unix Time : ").append(System.currentTimeMillis()).appendLine()
            sb.append("LocalTime : ").append(
                SimpleDateFormat.getDateTimeInstance().format(Date(System.currentTimeMillis()))
            ).appendLine()
            sb.append("Android Version : ").append(Build.VERSION.RELEASE).appendLine()
            sb.append("SDK Version : ").append(Build.VERSION.SDK_INT).appendLine()
            sb.append("Brand : ").append(Build.BRAND).appendLine()
            sb.append("Manufacturer : ").append(Build.MANUFACTURER).appendLine()
            sb.append("Target Sdk : ")
                .append(application!!.applicationInfo.targetSdkVersion.toString()).appendLine()
            sb.append("Flavour : ").append(
                if (isFdroid) {
                    "FDroid"
                } else {
                    "PlayStore"
                }
            ).appendLine()
            sb.append("Model : ").append(Build.MODEL).appendLine().appendLine()


            sb.append("Error Message : ").append(intent.getStringExtra("msg")).appendLine()
            sb.append("Error Cause : ").append(intent.getStringExtra("error_cause")).appendLine()
            sb.append("Error StackTrace : ").appendLine()
                .append(intent.getStringExtra("stacktrace"))


            editor.setText(sb.toString())
            editor.editable = false

            runCatching { SetupEditor(editor, application, lifecycleScope) }
            editor.isWordwrap = false
        }.onFailure {
            logErrorOrExit(it)
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
        val id = item.itemId

        when (id) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }

            R.id.copy_error -> {
                runCatching {
                    copyToClipboard(this, editor.text.toString())
                    toast("Copied")
                }.onFailure {
                    logErrorOrExit(it)
                }
            }

            R.id.report_issue -> {
                runCatching {
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
                }.onFailure {
                    logErrorOrExit(it)
                }

            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("crashInfo", text)
        clipboard.setPrimaryClip(clip)
    }
}
