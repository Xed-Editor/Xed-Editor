package com.rk.xededitor.ui.activities.settings

import android.content.Context
import android.os.Bundle
import android.system.Os
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.theme.KarbonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths

class Terminal : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KarbonTheme {
                Surface {
                    //Terminal()
                    TerminalScreen(this)
                }
            }
        }
    }


    @Composable
    fun TerminalScreen(context: Context) {
        var isDownloading by remember { mutableStateOf(true) }
        var progress by remember { mutableFloatStateOf(0f) }
        val coroutineScope = rememberCoroutineScope()

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isDownloading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Running Setup please wait...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )
                }
            } else {
                LaunchedEffect(Unit) {
                    setupEnvironment(
                        context = context,
                        onProgress = { downloadedBytes, totalBytes ->
                            progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                        },
                        onComplete = {
                            isDownloading = false
                        },
                        onError = { error ->
                            rkUtils.toast("Setup Failed: ${error.message}")
                            finish()
                        }
                    )
                }
            }
        }
    }

    private suspend fun setupEnvironment(
        context: Context,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Create symlink if it doesn't exist
                if (!Files.exists(
                        Paths.get(context.localBinDir().absolutePath, "loader"),
                        LinkOption.NOFOLLOW_LINKS
                    )) {
                    Os.symlink(
                        context.applicationInfo.nativeLibraryDir + "/libloader.so",
                        Paths.get(context.localBinDir().absolutePath, "loader").toString()
                    )
                }

                // Download the file
                val outputFile = File(context.localLibDir(), "libtalloc.so.2").also {
                    it.createNewFile()
                }

                downloadFile(
                    context = context,
                    url = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/x86_64/libtalloc.so.2",
                    outputFile = outputFile,
                    onProgress = onProgress
                )

                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    private suspend fun downloadFile(
        context: Context,
        url: String,
        outputFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Failed to download file: ${response.code}")

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

fun Context.localDir(): File {
    return File(filesDir.parentFile, "local").also {
        if (it.exists().not()) {
            it.mkdirs()
        }
    }
}

fun Context.localBinDir(): File {
    return File(filesDir.parentFile, "local/bin").also {
        if (it.exists().not()) {
            it.mkdirs()
        }
    }
}

fun Context.localLibDir(): File {
    return File(filesDir.parentFile, "local/lib").also {
        if (it.exists().not()) {
            it.mkdirs()
        }
    }
}


