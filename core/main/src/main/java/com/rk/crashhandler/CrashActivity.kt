package com.rk.crashhandler

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.pm.PackageInfoCompat
import com.rk.editor.Editor
import com.rk.utils.origin
import com.rk.utils.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.BuildConfig
import com.rk.theme.XedTheme
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.system.exitProcess
import androidx.core.net.toUri

class CrashActivity : ComponentActivity() {

    companion object {
        fun Context.isModified(): Boolean {
            if (BuildConfig.DEBUG) {
                return false
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    packageName,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures
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
                                                contentDescription = "Back"
                                            )
                                        }
                                    },
                                    title = { Text(strings.error.getString()) },
                                    actions = {
                                        TextButton(onClick = {
                                            runCatching {
                                                copyToClipboard(context, crashText)
                                                toast(strings.copied.getString())
                                            }.onFailure { logErrorOrExit(it) }
                                        }) {
                                            Text(stringResource(strings.copy))
                                        }

                                        TextButton(onClick = {
                                            runCatching {
                                                val url =
                                                    "https://github.com/Xed-Editor/Xed-Editor/issues/new?title=Crash%20Report&body=" +
                                                            URLEncoder.encode("``` \n$crashText\n ```", StandardCharsets.UTF_8.toString())
                                                val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                                                context.startActivity(browserIntent)
                                            }.onFailure { logErrorOrExit(it) }
                                        }) {
                                            Text(stringResource(strings.report_issue))
                                        }
                                    }
                                )
                                HorizontalDivider()
                            }
                        },
                    ) { paddingValues ->
                        val surfaceColor = if (isSystemInDarkTheme()) {
                            MaterialTheme.colorScheme.surfaceDim
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                        val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
                        val highSurfaceContainer = MaterialTheme.colorScheme.surfaceContainerHigh
                        val selectionColors = LocalTextSelectionColors.current
                        val realSurface = MaterialTheme.colorScheme.surface
                        val selectionBackground = selectionColors.backgroundColor
                        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                        val colorPrimary = MaterialTheme.colorScheme.primary
                        val colorPrimaryContainer = MaterialTheme.colorScheme.primaryContainer
                        val colorSecondary = MaterialTheme.colorScheme.secondary
                        val handleColor = selectionColors.handleColor
                        val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

                        val gutterColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        val currentLineColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.8f)

                        val divider = MaterialTheme.colorScheme.outlineVariant
                        val isDarkMode = isSystemInDarkTheme()

                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                            factory = { context ->
                                Editor(context).apply {
                                    setTextSize(10f)
                                    setText(crashText)
                                    editable = false
                                    isWordwrap = false
                                    setThemeColors(
                                        isDarkMode = isDarkMode,
                                        editorSurface = surfaceColor.toArgb(),
                                        surfaceContainer = surfaceContainer.toArgb(),
                                        highSurfaceContainer = highSurfaceContainer.toArgb(),
                                        surface = realSurface.toArgb(),
                                        onSurface = onSurfaceColor.toArgb(),
                                        colorPrimary = colorPrimary.toArgb(),
                                        colorPrimaryContainer = colorPrimaryContainer.toArgb(),
                                        colorSecondary = colorSecondary.toArgb(),
                                        secondaryContainer = secondaryContainer.toArgb(),
                                        selectionBg = selectionBackground.toArgb(),
                                        handleColor = handleColor.toArgb(),
                                        gutterColor = gutterColor.toArgb(),
                                        currentLine = currentLineColor.toArgb(),
                                        dividerColor = divider.toArgb()
                                    )
                                }
                            },
                            update = { editor ->
                                editor.setText(crashText)
                            }
                        )

                    }
                }
            }
        }.onFailure {
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
            append("Unexpected Crash occurred").appendLine().appendLine()

            append("Thread : ").append(intent.getStringExtra("thread")).appendLine()
            append("App Version : ").append(versionName).appendLine()
            append("Version Code : ").append(versionCode).appendLine()
            append("Modified : ").append(isModified()).appendLine()
            append("Commit hash : ").append(BuildConfig.GIT_COMMIT_HASH.substring(0, 8)).appendLine()
            append("PackageName : ").append(application!!.packageName).appendLine()
            append("Commit date : ").append(BuildConfig.GIT_COMMIT_DATE).appendLine()
            append("Origin : ").append(origin()).appendLine()
            append("Unix Time : ").append(System.currentTimeMillis()).appendLine()
            append("LocalTime : ").append(SimpleDateFormat.getDateTimeInstance().format(Date())).appendLine()
            append("Android Version : ").append(Build.VERSION.RELEASE).appendLine()
            append("SDK Version : ").append(Build.VERSION.SDK_INT).appendLine()
            append("Brand : ").append(Build.BRAND).appendLine()
            append("Manufacturer : ").append(Build.MANUFACTURER).appendLine()
            append("Target Sdk : ").append(application!!.applicationInfo.targetSdkVersion.toString()).appendLine()
            append("Model : ").append(Build.MODEL).appendLine()
            append("Used Memory: ").append(usedMem).append("MB").appendLine()
            append("Max Memory: ").append(maxMem).append("MB").appendLine()

            appendLine()

            append("Error Message : ").append(intent.getStringExtra("msg")).appendLine()
            append("Error Cause : ").append(intent.getStringExtra("error_cause")).appendLine()
            append("Error StackTrace : ").appendLine()
                .append(intent.getStringExtra("stacktrace"))
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("crashInfo", text)
        clipboard.setPrimaryClip(clip)
    }
}