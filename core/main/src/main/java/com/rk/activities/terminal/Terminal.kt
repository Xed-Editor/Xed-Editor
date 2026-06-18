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
import com.rk.exec.isTerminalInstalled
import com.rk.file.child
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.terminal.NEXT_STAGE
import com.rk.terminal.SessionService
import com.rk.terminal.TerminalBackEnd
import com.rk.terminal.TerminalScreen
import com.rk.terminal.changeSession

import com.rk.theme.XedTheme
import com.rk.utils.errorDialog
import com.rk.utils.toast
import java.io.File
import java.lang.ref.WeakReference
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Terminal : AppCompatActivity() {
    var sessionBinder by mutableStateOf<WeakReference<SessionService.SessionBinder>?>(null)
    var isBound = false
    var terminalViewRef = WeakReference<com.termux.view.TerminalView?>(null)
    var virtualKeysViewRef = WeakReference<com.rk.terminal.virtualkeys.VirtualKeysView?>(null)

    val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as SessionService.SessionBinder
                sessionBinder = WeakReference(binder)
                // sessionBinder = WeakReference(binder)
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
        terminalViewRef.get() ?: return

        val pwd = intent.getStringExtra("cwd")
        if (pwd == null) {
            return
        }
        val sessionId = File(pwd).name

        lifecycleScope.launch(Dispatchers.Main) {
            val client = TerminalBackEnd(this@Terminal)
            val info = binder.getSessionInfoByPwd(pwd) ?: binder.createSession(sessionId, client, this@Terminal)

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

    @OptIn(DelicateCoroutinesApi::class)
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

    @OptIn(DelicateCoroutinesApi::class)
    @Composable
    fun TerminalScreenHost(context: Context) {
        var progress by remember { mutableFloatStateOf(0f) }
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
                needsDownload = isTerminalInstalled().not()
                if (needsDownload) {
                    setupEnvironment(
                        context = context,
                        onProgress = { fileName, downloaded, total ->
                            downloadedBytes = downloaded
                            totalBytes = total
                            currentFileName = fileName

                            if (total > 0) {
                                val downloadedMB = formatBytesToMB(downloaded)
                                val totalMB = formatBytesToMB(total)
                                progressText =
                                    "${strings.installing.getString()} ($downloadedMB/$totalMB MB)"
                            }
                        },
                        onComplete = { installNextStage = it },
                        onError = { error ->
                            error.printStackTrace()
                            errorDialog(msg = "Setup failed: ${error.message}")
                            finish()
                        },
                    )
                } else {
                    installNextStage = NEXT_STAGE.NONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                toast("Setup failed: ${e.message}")
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

                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(0.8f))

                            if (totalBytes > 0) {
                                val percent = (downloadedBytes.toFloat() / totalBytes * 100).toInt()
                                progress = downloadedBytes.toFloat() / totalBytes

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

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun setupEnvironment(
        context: Context,
        onProgress: (fileName: String, downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: (NEXT_STAGE) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val usrDir = File(context.filesDir, "usr")
                if (usrDir.exists()) {
                    usrDir.deleteRecursively()
                }
                usrDir.mkdirs()

                val abis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                val arch = Build.SUPPORTED_ABIS.firstOrNull { abis.contains(it) } ?: "arm64-v8a"
                var urlString = "https://github.com/Xed-Editor/Xed-Editor/releases/download/v3.2.9/bootstrap-$arch.zip"
                
                var connection: java.net.HttpURLConnection
                var redirectCount = 0
                val maxRedirects = 5
                
                while (true) {
                    val url = java.net.URL(urlString)
                    connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 30_000
                        readTimeout = 30_000
                        requestMethod = "GET"
                        instanceFollowRedirects = true
                    }
                    
                    val responseCode = connection.responseCode
                    if (responseCode == java.net.HttpURLConnection.HTTP_MULT_CHOICE ||
                        responseCode == java.net.HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == java.net.HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == 307 || responseCode == 308
                    ) {
                        val newUrl = connection.getHeaderField("Location")
                        connection.disconnect()
                        if (newUrl != null && redirectCount < maxRedirects) {
                            urlString = newUrl
                            redirectCount++
                            continue
                        }
                    }
                    break
                }
                
                val responseCode = connection.responseCode
                if (responseCode == java.net.HttpURLConnection.HTTP_NOT_FOUND) {
                    throw java.io.IOException("Bootstrap zip for your CPU architecture ($arch) is not supported or published")
                } else if (responseCode !in 200..299) {
                    throw java.io.IOException("Server returned HTTP response code: $responseCode for URL: $urlString")
                }
                
                val totalBytes: Long = connection.contentLengthLong
                val assetStream = connection.inputStream
                val assetName = "bootstrap-$arch.zip"

                val countingStream = object : java.io.FilterInputStream(assetStream) {
                    var bytesRead = 0L
                    override fun read(): Int {
                        val result = super.read()
                        if (result != -1) bytesRead++
                        return result
                    }
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val result = super.read(b, off, len)
                        if (result != -1) bytesRead += result
                        return result
                    }
                }

                val zipInputStream = java.util.zip.ZipInputStream(countingStream)
                var entry = zipInputStream.nextEntry
                val symlinkLines = mutableListOf<String>()

                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        if (name == "SYMLINKS.txt") {
                            val bos = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(1024)
                            var len: Int
                            while (zipInputStream.read(buffer).also { len = it } > 0) {
                                bos.write(buffer, 0, len)
                            }
                            val content = bos.toString("UTF-8")
                            symlinkLines.addAll(content.lines().filter { it.contains("←") })
                        } else {
                            val targetFile = File(usrDir, name)
                            targetFile.parentFile?.mkdirs()
                            targetFile.outputStream().use { output ->
                                zipInputStream.copyTo(output)
                            }
                            if (name.startsWith("bin/") || name.endsWith(".so") || name.contains("/bin/") || name.contains("/lib/")) {
                                targetFile.setExecutable(true, false)
                                targetFile.setReadable(true, false)
                            }
                        }
                    }
                    onProgress(assetName, countingStream.bytesRead, totalBytes)
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
                zipInputStream.close()

                // Process symlinks
                for (line in symlinkLines) {
                    val parts = line.split("←")
                    if (parts.size == 2) {
                        val target = parts[0]
                        var path = parts[1]
                        if (path.startsWith("./")) {
                            path = path.substring(2)
                        }
                        val linkFile = File(usrDir, path)
                        linkFile.parentFile?.mkdirs()
                        if (linkFile.exists()) {
                            linkFile.delete()
                        }
                        try {
                            android.system.Os.symlink(target, linkFile.absolutePath)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // Touch the marker file
                com.rk.file.localDir(context).child(".terminal_setup_ok_DO_NOT_REMOVE").createNewFile()

                onComplete(NEXT_STAGE.NONE)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }
}
