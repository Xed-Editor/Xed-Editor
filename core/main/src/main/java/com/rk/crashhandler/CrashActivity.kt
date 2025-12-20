package com.rk.crashhandler

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.rk.crashhandler.CrashHandler.logErrorOrExit
import com.rk.editor.Editor
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.theme.XedTheme
import com.rk.utils.copyToClipboard
import com.rk.utils.origin
import com.rk.xededitor.BuildConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.system.exitProcess

class CrashActivity : ComponentActivity() {
    companion object {
        fun Context.isModified(): Boolean {
            val signatures =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageManager
                        .getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                        .signingInfo
                        ?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    packageManager
                        .getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
                        .signatures
                }

            if (signatures == null) {
                return true
            }

            for (signature in signatures) {
                val cert =
                    CertificateFactory.getInstance("X.509").generateCertificate(signature.toByteArray().inputStream())
                        as X509Certificate
                val sha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                val hex = sha256.joinToString(":") { "%02X".format(it) }

                if (hex.equals(assets.open("hash").bufferedReader().use { it.readText() }, ignoreCase = true)) {
                    return false
                }
            }
            return true
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
                enableEdgeToEdge()
                val crashText = buildCrashReport()

                setContent {
                    val context = LocalContext.current

                    XedTheme {
                        Scaffold(
                            topBar = {
                                Column {
                                    TopAppBar(
                                        navigationIcon = {
                                            IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Back",
                                                )
                                            }
                                        },
                                        title = { Text(strings.error.getString()) },
                                        actions = {
                                            TextButton(
                                                onClick = {
                                                    runCatching { copyToClipboard("crash_report", crashText, true) }
                                                        .onFailure { logErrorOrExit(it) }
                                                }
                                            ) {
                                                Text(stringResource(strings.copy))
                                            }

                                            val showReport = remember {
                                                intent.getBooleanExtra("force_crash", false).not()
                                            }

                                            if (showReport) {
                                                TextButton(
                                                    onClick = {
                                                        runCatching {
                                                                val url =
                                                                    "https://github.com/Xed-Editor/Xed-Editor/issues/new?title=Crash%20Report&body=" +
                                                                        URLEncoder.encode(
                                                                            "``` \n$crashText\n ```",
                                                                            StandardCharsets.UTF_8.toString(),
                                                                        )
                                                                val browserIntent =
                                                                    Intent(Intent.ACTION_VIEW, url.toUri())
                                                                context.startActivity(browserIntent)
                                                            }
                                                            .onFailure { logErrorOrExit(it) }
                                                    }
                                                ) {
                                                    Text(stringResource(strings.report_issue))
                                                }
                                            }
                                        },
                                    )
                                    HorizontalDivider()
                                }
                            }
                        ) { paddingValues ->
                            val selectionColors = LocalTextSelectionColors.current
                            val isDarkMode = isSystemInDarkTheme()
                            val colorScheme = MaterialTheme.colorScheme

                            AndroidView(
                                modifier = Modifier.fillMaxSize().padding(paddingValues),
                                factory = { context ->
                                    Editor(context).apply {
                                        setTextSize(10f)
                                        setText(crashText)
                                        editable = false
                                        isWordwrap = false
                                        setThemeColors(
                                            isDarkMode = isDarkMode,
                                            selectionColors = selectionColors,
                                            colorScheme = colorScheme,
                                        )
                                    }
                                },
                                update = { editor -> editor.setText(crashText) },
                            )
                        }
                    }
                }
            }
            .onFailure {
                logErrorOrExit(it)
                it.printStackTrace()
                runCatching { finishAffinity() }
                exitProcess(1)
            }
    }

    private fun buildCrashReport(): String {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) // MB
        val maxMem = runtime.maxMemory() / (1024 * 1024)

        return buildString {
            append("Unexpected crash occurred").appendLine().appendLine()

            append("Thread: ").append(intent.getStringExtra("thread")).appendLine()
            append("App version: ").append(versionName).appendLine()
            append("Version code: ").append(versionCode).appendLine()
            append("Modified: ").append(isModified()).appendLine()
            append("Commit hash: ").append(BuildConfig.GIT_COMMIT_HASH.substring(0, 8)).appendLine()
            append("Package name: ").append(application!!.packageName).appendLine()
            append("Commit date: ").append(BuildConfig.GIT_COMMIT_DATE).appendLine()
            append("Origin: ").append(origin()).appendLine()
            append("Unix Time: ").append(System.currentTimeMillis()).appendLine()
            append("Local time: ").append(SimpleDateFormat.getDateTimeInstance().format(Date())).appendLine()
            append("Android version: ").append(Build.VERSION.RELEASE).appendLine()
            append("SDK version: ").append(Build.VERSION.SDK_INT).appendLine()
            append("Brand: ").append(Build.BRAND).appendLine()
            append("Manufacturer: ").append(Build.MANUFACTURER).appendLine()
            append("Target SDK: ").append(application!!.applicationInfo.targetSdkVersion.toString()).appendLine()
            append("Model: ").append(Build.MODEL).appendLine()
            append("Used memory: ").append(usedMem).append("MB").appendLine()
            append("Max memory: ").append(maxMem).append("MB").appendLine()

            appendLine()

            append("Error message: ").append(intent.getStringExtra("msg")).appendLine()
            append("Error cause: ").append(intent.getStringExtra("error_cause")).appendLine()
            append("Error stacktrace: ").appendLine().append(intent.getStringExtra("stacktrace"))
        }
    }
}
