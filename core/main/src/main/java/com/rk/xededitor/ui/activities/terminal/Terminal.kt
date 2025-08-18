package com.rk.xededitor.ui.activities.terminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.App.Companion.getTempDir
import com.rk.SessionService
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.localDir
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.libcommons.*
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.ui.FPSBooster
import com.rk.xededitor.ui.screens.terminal.TerminalScreen
import com.rk.xededitor.ui.screens.terminal.setupRootfs
import com.rk.xededitor.ui.theme.KarbonTheme
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.lang.ref.WeakReference
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class Terminal : AppCompatActivity() {
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
        FPSBooster(this)
        enableEdgeToEdge()
        setContent {
            KarbonTheme {
                Surface {
                    TerminalScreenHost(this)
                }
            }
        }
    }

    var progressText by mutableStateOf(strings.installing.getString())
    var isSettingUp by mutableStateOf(false)

    @OptIn(DelicateCoroutinesApi::class)
    @Composable
    fun TerminalScreenHost(context: Context) {
        var progress by remember { mutableFloatStateOf(0f) }
        var isSetupComplete by remember { mutableStateOf(false) }
        var needsDownload by remember { mutableStateOf(false) }
        var currentFileName by remember { mutableStateOf("") }
        var downloadedBytes by remember { mutableLongStateOf(0L) }
        var totalBytes by remember { mutableLongStateOf(0L) }

        // Helper function to format bytes to MB string
        fun formatBytesToMB(bytes: Long): String {
            return "%.2f".format(bytes / (1024.0 * 1024.0))
        }

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


                val rootfsFiles = sandboxDir().listFiles()?.filter {
                    it.absolutePath != sandboxHomeDir().absolutePath && it.absolutePath != sandboxDir().child(
                        "tmp"
                    ).absolutePath
                } ?: emptyList()
                if (rootfsFiles.isEmpty()) {
                    filesToDownload.add(
                        DownloadFile(
                            url = if (abi.contains("x86_64")) {
                                sandbox_x86_64
                            } else if (abi.contains("arm64-v8a")) {
                                sandbox_aarch64
                            } else if (abi.contains("armeabi-v7a")) {
                                sandbox_arm
                            } else {
                                throw RuntimeException("Unsupported CPU")
                            }, outputPath = "tmp/sandbox.tar.gz"
                        )
                    )

                }

                needsDownload = filesToDownload.any { file ->
                    !File(context.filesDir.parentFile, file.outputPath).exists()
                }

                setupEnvironment(context = context,
                    filesToDownload = filesToDownload,
                    onProgress = { fileName, downloaded, total ->
                        downloadedBytes = downloaded
                        totalBytes = total
                        currentFileName = fileName

                        if (total > 0) {
                            val downloadedMB = formatBytesToMB(downloaded)
                            val totalMB = formatBytesToMB(total)
                            progressText = "${strings.downloading.getString()} ${fileName.removeSuffix(".so").removePrefix("lib")} ($downloadedMB/$totalMB MB)"
                        }
                    },
                    onComplete = {
                        isSetupComplete = true
                    },
                    onError = { error,file ->
                        if (error is UnknownHostException) {
                            toast(strings.network_err.getString())
                        }else if (error is SocketTimeoutException){
                            errorDialog(strings.timeout)
                        } else {
                            error.printStackTrace()
                            GlobalScope.launch(Dispatchers.IO){
                                if (file?.absolutePath?.contains(localBinDir().absolutePath) == true){
                                    localBinDir().deleteRecursively()
                                }

                                if (file?.name == "sandbox.tar.gz"){
                                    sandboxDir().deleteRecursively()
                                    File(getTempDir(), "sandbox.tar.gz").delete()
                                }
                            }
                            errorDialog("Setup Failed: ${error.message}")
                        }
                        finish()


                    })
            } catch (e: Exception) {
                if (e is UnknownHostException) {
                    toast(strings.network_err.getString())
                }else if (e is SocketTimeoutException){
                    errorDialog(strings.timeout)
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
                            text = progressText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (isSettingUp){
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(0.8f),
                            )
                        }else{
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(0.8f),
                            )
                            if (totalBytes > 0) {
                                val percent = (downloadedBytes.toFloat() / totalBytes * 100).toInt()
                                progress = (downloadedBytes.toFloat() / totalBytes * 1)
                                Text(
                                    text = "${percent}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }



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
        onProgress: (fileName: String, downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception, File?) -> Unit
    ) {
        var currentFile: File? = null

        withContext(Dispatchers.IO) {
            try {
                var completedFiles = 0

                filesToDownload.forEach { file ->
                    val outputFile = File(context.filesDir.parentFile, file.outputPath)
                    currentFile = outputFile

                    outputFile.parentFile?.mkdirs()

                    if (!outputFile.exists()) {
                        downloadFile(
                            url = file.url,
                            outputFile = outputFile,
                            onProgress = { downloaded, total ->
                                onProgress(file.outputPath.substringAfterLast("/"), downloaded, total)
                            }
                        )
                    } else {
                        // Report existing file as already downloaded
                        onProgress(file.outputPath.substringAfterLast("/"), outputFile.length(), outputFile.length())
                    }
                    completedFiles++
//                    withContext(Dispatchers.Main) {
//                        onProgress(completedFiles, totalFiles, totalProgress)
//                    }

                    runCatching {
                        outputFile.setExecutable(true)
                    }.onFailure { it.printStackTrace() }
                }

                progressText = "Getting everything ready..."
                isSettingUp = true
                setupRootfs(this@Terminal,onComplete = {
                    if (!it.isNullOrBlank()){
                        errorDialog(it)
                    }

                    runOnUiThread {
                        isSettingUp = false
                        onComplete()
                    }

                })

            } catch (e: Exception) {
                e.printStackTrace()
                localDir().deleteRecursively()
                withContext(Dispatchers.Main) {
                    onError(e,currentFile)
                }
            }
        }
    }

    private suspend fun downloadFile(
        url: String, outputFile: File, onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.MINUTES)
                .readTimeout(1, TimeUnit.MINUTES)
                .writeTimeout(1, TimeUnit.MINUTES)
                .callTimeout(10, TimeUnit.MINUTES).build()
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

private const val sandbox_arm =
    "https://cdimage.ubuntu.com/ubuntu-base/releases/plucky/release/ubuntu-base-25.04-base-armhf.tar.gz"
private const val sandbox_aarch64 =
    "https://cdimage.ubuntu.com/ubuntu-base/releases/plucky/release/ubuntu-base-25.04-base-arm64.tar.gz"
private const val sandbox_x86_64 =
    "https://cdimage.ubuntu.com/ubuntu-base/releases/plucky/release/ubuntu-base-25.04-base-amd64.tar.gz"