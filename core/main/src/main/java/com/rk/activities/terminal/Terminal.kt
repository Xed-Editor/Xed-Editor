package com.rk.activities.terminal

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rk.XedConstants
import com.rk.exec.isTerminalInstalled
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.localLibDir
import com.rk.file.sandboxDir
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.terminal.NEXT_STAGE
import com.rk.terminal.SessionService
import com.rk.terminal.TerminalBackEnd
import com.rk.terminal.TerminalScreen
import com.rk.terminal.changeSession
import com.rk.terminal.getNextStage
import com.rk.terminal.terminalView
import com.rk.theme.XedTheme
import com.rk.utils.errorDialog
import com.rk.utils.getTempDir
import com.rk.utils.toast
import java.io.File
import java.lang.ref.WeakReference
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class Terminal : AppCompatActivity() {
    var sessionBinder by mutableStateOf<WeakReference<SessionService.SessionBinder>?>(null)
    var isBound = false

    val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as SessionService.SessionBinder
                sessionBinder = WeakReference(binder)
                isBound = true
                handleIntent(intent)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isBound = false
                sessionBinder = null
            }
        }

    override fun onStart() {
        super.onStart()
        ContextCompat.startForegroundService(this, Intent(this, SessionService::class.java))

        Intent(this, SessionService::class.java).also { intent ->
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        instance = this
    }

    fun handleIntent(intent: Intent) {
        this.intent = intent
        val binder = sessionBinder?.get() ?: return
        terminalView.get() ?: return

        val pwd = intent.getStringExtra("cwd")
        if (pwd == null) return
        val sessionId = File(pwd).name

        lifecycleScope.launch(Dispatchers.Main) {
            val client = TerminalBackEnd()
            val info = binder.getSessionInfoByPwd(pwd) ?: binder.createSession(sessionId, client, this@Terminal)!!

            this@Terminal.changeSession(info.id)
            setIntent(Intent())
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun needsNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private var activityRef = WeakReference<Terminal?>(null)
        var instance: Terminal?
            get() = activityRef.get()
            private set(value) {
                activityRef = WeakReference(value)
            }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        instance = this

        if (needsNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            XedTheme {
                Surface {
                    if (sessionBinder != null) {
                        TerminalScreenHost(this)
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Error: No service connection")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (!isFinishing) {
            super.onDestroy()
            return
        }
        super.onDestroy()
    }

    var progressText by mutableStateOf(strings.installing.getString())
    var installNextStage by mutableStateOf<NEXT_STAGE?>(null)

    private fun formatBytesToMB(bytes: Long): String =
        "%.2f".format(bytes / (1024.0 * 1024.0))

    @Composable
    fun TerminalScreenHost(context: Context) {
        var progress by remember { mutableFloatStateOf(0f) }
        var needsDownload by remember { mutableStateOf(false) }
        var currentFileName by remember { mutableStateOf("") }
        var downloadedBytes by remember { mutableLongStateOf(0L) }
        var totalBytes by remember { mutableLongStateOf(0L) }

        LaunchedEffect(Unit) {
            try {
                val abi = Build.SUPPORTED_ABIS

                val filesToDownload = buildList {
                    add(
                        DownloadFile(
                            url = when {
                                abi.contains("x86_64") -> XedConstants.TALLOC_X64
                                abi.contains("arm64-v8a") -> XedConstants.TALLOC_ARM64
                                abi.contains("armeabi-v7a") -> XedConstants.TALLOC_ARM
                                else -> throw RuntimeException("Unsupported CPU")
                            },
                            outputFile = localLibDir().child("libtalloc.so.2"),
                        )
                    )
                    add(
                        DownloadFile(
                            url = when {
                                abi.contains("x86_64") -> XedConstants.PROOT_X64
                                abi.contains("arm64-v8a") -> XedConstants.PROOT_ARM64
                                abi.contains("armeabi-v7a") -> XedConstants.PROOT_ARM
                                else -> throw RuntimeException("Unsupported CPU")
                            },
                            outputFile = localBinDir().child("proot"),
                        )
                    )
                    if (!isTerminalInstalled()) {
                        add(
                            DownloadFile(
                                url = when {
                                    abi.contains("x86_64") -> XedConstants.ROOTFS_X64
                                    abi.contains("arm64-v8a") -> XedConstants.ROOTFS_ARM64
                                    abi.contains("armeabi-v7a") -> XedConstants.ROOTFS_ARM
                                    else -> throw RuntimeException("Unsupported CPU")
                                },
                                outputFile = getTempDir().child("sandbox.tar.gz"),
                            )
                        )
                    }
                }

                needsDownload = filesToDownload.any { file -> file.outputFile.exists().not() }

                setupEnvironment(
                    context = context,
                    filesToDownload = filesToDownload,
                    onProgress = { fileName, downloaded, total ->
                        downloadedBytes = downloaded
                        totalBytes = total
                        currentFileName = fileName

                        if (total > 0) {
                            val downloadedMB = formatBytesToMB(downloaded)
                            val totalMB = formatBytesToMB(total)
                            progressText =
                                "${strings.downloading.getString()} ${fileName.removeSuffix(".so").removePrefix("lib")} ($downloadedMB/$totalMB MB)"
                        }
                    },
                    onComplete = { installNextStage = it },
                    onError = { error, file ->
                        when (error) {
                            is UnknownHostException -> toast(strings.network_err.getString())
                            is SocketTimeoutException -> errorDialog(strings.timeout)
                            else -> {
                                error.printStackTrace()
                                lifecycleScope.launch {
                                    if (file?.absolutePath?.contains(localBinDir().absolutePath) == true) {
                                        localBinDir().deleteRecursively()
                                    }
                                    if (file?.name == "sandbox.tar.gz") {
                                        sandboxDir().deleteRecursively()
                                        File(getTempDir(), "sandbox.tar.gz").delete()
                                    }
                                }
                                errorDialog("Setup failed: ${error.message}")
                            }
                        }
                        finish()
                    },
                )
            } catch (e: Exception) {
                when (e) {
                    is UnknownHostException -> toast(strings.network_err.getString())
                    is SocketTimeoutException -> errorDialog(strings.timeout)
                    else -> {
                        e.printStackTrace()
                        toast("Setup failed: ${e.message}")
                    }
                }
                finish()
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val context = LocalContext.current
            val activity = context as? Activity

            DisposableEffect(Unit) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }

            if (installNextStage == null) {
                if (needsDownload) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(text = progressText, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(16.dp))

                            val currentProgress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                            LinearProgressIndicator(progress = { currentProgress }, modifier = Modifier.fillMaxWidth(0.8f))

                            if (totalBytes > 0) {
                                val percent = (downloadedBytes.toFloat() / totalBytes * 100).toInt()
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }

                        Text(
                            text = stringResource(strings.warn_dont_leave_setup),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                TerminalScreen(terminalActivity = this@Terminal)
            }
        }
    }

    data class DownloadFile(val url: String, val outputFile: File)

    private suspend fun setupEnvironment(
        context: Context,
        filesToDownload: List<DownloadFile>,
        onProgress: (fileName: String, downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: (NEXT_STAGE) -> Unit,
        onError: (Exception, File?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        var currentFile: File? = null

        try {
            filesToDownload.forEach { file ->
                val outputFile = file.outputFile
                currentFile = outputFile

                outputFile.parentFile?.mkdirs()

                if (!outputFile.exists()) {
                    downloadFile(
                        url = file.url,
                        outputFile = outputFile,
                        onProgress = { downloaded, total -> onProgress(file.outputFile.name, downloaded, total) },
                    )
                } else {
                    onProgress(file.outputFile.name, outputFile.length(), outputFile.length())
                }

                runCatching { outputFile.setExecutable(true) }.onFailure { it.printStackTrace() }
            }

            val stage = getNextStage(this@Terminal)
            onComplete(stage)
        } catch (e: Exception) {
            e.printStackTrace()
            onError(e, currentFile)
            if (currentFile?.exists() == true) {
                currentFile.delete()
            }
        }
    }

    private suspend fun downloadFile(
        url: String,
        outputFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        val client = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .writeTimeout(1, TimeUnit.MINUTES)
            .callTimeout(10, TimeUnit.MINUTES)
            .build()
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
                        withContext(Dispatchers.Main) { onProgress(downloadedBytes, totalBytes) }
                    }
                }
            }
        }
    }
}