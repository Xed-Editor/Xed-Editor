package com.rk.xededitor.ui.activities.terminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.SessionService
import com.rk.libcommons.*
import com.rk.libcommons.alpineDir
import com.rk.libcommons.localDir
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.ui.screens.terminal.MkRootfs
import com.rk.xededitor.ui.screens.terminal.TerminalScreen
import com.rk.xededitor.ui.theme.KarbonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.lang.ref.WeakReference
import java.net.UnknownHostException

class Terminal : ComponentActivity() {
    var sessionBinder = WeakReference<SessionService.SessionBinder?>(null)
    var isBound = false


    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SessionService.SessionBinder
            sessionBinder = WeakReference(binder)
            isBound = true

        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            sessionBinder = WeakReference(null)
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, SessionService::class.java).also { intent ->
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KarbonTheme {
                Surface {
                    TerminalScreenHost(this)
                }
            }
        }
    }

    @Composable
    fun TerminalScreenHost(context: Context) {
        var progress by remember { mutableFloatStateOf(0f) }
        var progressText by remember { mutableStateOf(strings.installing.getString()) }
        var isSetupComplete by remember { mutableStateOf(false) }
        var needsDownload by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            try {
                val abi = Build.SUPPORTED_ABIS

                val filesToDownload = listOf(
                    DownloadFile(
                        url = if (abi.contains("x86_64")) {
                            talloc_x86_64
                        } else if (abi.contains("arm64-v8a")) {
                            talloc_aarch64
                        } else if (abi.contains("armeabi-v7a")) {
                            talloc_arm
                        } else {
                            throw RuntimeException("Unsupported CPU")
                        }, outputPath = "local/lib/libtalloc.so.2"
                    ),

                    DownloadFile(
                        url = if (abi.contains("x86_64")) {
                            proot_x86_64
                        } else if (abi.contains("arm64-v8a")) {
                            proot_aarch64
                        } else if (abi.contains("armeabi-v7a")) {
                            proot_arm
                        } else {
                            throw RuntimeException("Unsupported CPU")
                        }, outputPath = "local/bin/proot"
                    ),
                ).toMutableList()


                val rootfsFiles = alpineDir().listFiles()?.filter {
                    it.absolutePath != alpineHomeDir().absolutePath && it.absolutePath != alpineDir().child(
                        "tmp"
                    ).absolutePath
                } ?: emptyList()
                if (rootfsFiles.isEmpty()) {
                    filesToDownload.add(
                        DownloadFile(
                            url = if (abi.contains("x86_64")) {
                                alpine_x86_64
                            } else if (abi.contains("arm64-v8a")) {
                                alpine_aarch64
                            } else if (abi.contains("armeabi-v7a")) {
                                alpine_arm
                            } else {
                                throw RuntimeException("Unsupported CPU")
                            }, outputPath = "tmp/alpine.tar.gz"
                        )
                    )

                }

                needsDownload = filesToDownload.any { file ->
                    !File(context.filesDir.parentFile, file.outputPath).exists()
                }

                setupEnvironment(context = context,
                    filesToDownload = filesToDownload,
                    onProgress = { completedFiles, totalFiles, currentProgress ->
                        if (needsDownload) {
                            val fileProgress = completedFiles.toFloat() / totalFiles.toFloat()
                            val combinedProgress = (fileProgress + currentProgress) / totalFiles
                            progress = combinedProgress.coerceIn(
                                0f, 1f
                            )
                            progressText =
                                "${strings.downloading.getString()} ${(progress * 100).toInt()}%"
                        }
                    },
                    onComplete = {
                        isSetupComplete = true
                    },
                    onError = { error ->
                        if (error is UnknownHostException) {
                            toast(strings.network_err.getString())
                        } else {
                            error.printStackTrace()
                            toast("Setup Failed: ${error.message}")
                        }
                        finish()


                    })
            } catch (e: Exception) {
                if (e is UnknownHostException) {
                    toast(strings.network_err.getString())
                } else {
                    e.printStackTrace()
                    toast("Setup Failed: ${e.message}")
                }
                finish()
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            if (!isSetupComplete) {
                if (needsDownload) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = progressText, style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(0.8f),
                        )
                    }
                }
            } else {
                TerminalScreen(terminalActivity = this@Terminal)
            }
        }
    }


    data class DownloadFile(
        val url: String, val outputPath: String
    )

    private suspend fun setupEnvironment(
        context: Context,
        filesToDownload: List<DownloadFile>,
        onProgress: (completedFiles: Int, totalFiles: Int, currentProgress: Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {

                var completedFiles = 0
                val totalFiles = filesToDownload.size
                var totalProgress = 0f

                filesToDownload.forEach { file ->
                    val outputFile = File(context.filesDir.parentFile, file.outputPath)

                    outputFile.parentFile?.mkdirs()

                    if (!outputFile.exists()) {
                        outputFile.createNewFile()

                        downloadFile(url = file.url,
                            outputFile = outputFile,
                            onProgress = { downloadedBytes, totalBytes ->
                                val currentFileProgress =
                                    downloadedBytes.toFloat() / totalBytes.toFloat()
                                totalProgress = (completedFiles + currentFileProgress) / totalFiles

                                runOnUiThread {
                                    onProgress(completedFiles, totalFiles, totalProgress)
                                }

                            })
                    }
                    completedFiles++
                    withContext(Dispatchers.Main) {
                        onProgress(completedFiles, totalFiles, totalProgress)
                    }

                    runCatching {
                        outputFile.setExecutable(true)
                    }.onFailure { it.printStackTrace() }
                }

                MkRootfs(this@Terminal) {
                    runOnUiThread {
                        onComplete()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                localDir().deleteRecursively()
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    private suspend fun downloadFile(
        url: String, outputFile: File, onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Failed to download file: ${response.code}")
                }

                val body = response.body ?: throw Exception("Empty response body")
                val totalBytes = body.contentLength()


                var downloadedBytes = 0L

                outputFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            withContext(Dispatchers.Main) {
                                onProgress(downloadedBytes, totalBytes)
                            }
                        }
                    }
                }
            }
        }
    }
}


private const val talloc_arm =
    "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/arm/libtalloc.so.2"
private const val talloc_aarch64 =
    "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/aarch64/libtalloc.so.2"
private const val talloc_x86_64 =
    "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/x86_64/libtalloc.so.2"
private const val proot_arm =
    "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/arm/proot"
private const val proot_aarch64 =
    "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/aarch64/proot"
private const val proot_x86_64 =
    "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/x86_64/proot"
private const val alpine_arm =
    "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/armhf/alpine-minirootfs-3.21.0-armhf.tar.gz"
private const val alpine_aarch64 =
    "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz"
private const val alpine_x86_64 =
    "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/x86_64/alpine-minirootfs-3.21.0-x86_64.tar.gz"