package com.rk.terminal

import android.app.Activity
import android.content.Context
import android.os.Build
import com.rk.XedConstants
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.LoadingPopup
import com.rk.utils.getTempDir
import com.rk.utils.isMainThread
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import androidx.appcompat.app.AppCompatActivity

enum class NEXT_STAGE {
    NONE,
    EXTRACTION,
}

suspend fun getNextStage(context: Context): NEXT_STAGE {
    if (isMainThread()) {
        throw RuntimeException("IO operation on the main thread")
    }

    val sandboxFile = File(getTempDir(), "sandbox.tar.gz")
    val rootfsFiles =
        sandboxDir().listFiles()?.filter {
            it.absolutePath != sandboxHomeDir().absolutePath &&
                it.absolutePath != sandboxDir().child("tmp").absolutePath
        } ?: emptyList()

    if (rootfsFiles.isEmpty()) {
        if (!sandboxFile.exists()) {
            downloadRootfs(context, sandboxFile)
        }
        return NEXT_STAGE.EXTRACTION
    }

    return NEXT_STAGE.NONE
}

private fun downloadRootfs(context: Context, outputFile: File) {
    val activity = context as? AppCompatActivity
    val loadingPopup = activity?.let {
        LoadingPopup(it).setMessage(strings.downloading.getString()).show()
    }

    val url = when {
        Build.SUPPORTED_ABIS.contains("arm64-v8a") -> XedConstants.ROOTFS_ARM64
        Build.SUPPORTED_ABIS.contains("x86_64") -> XedConstants.ROOTFS_X64
        else -> XedConstants.ROOTFS_ARM
    }

    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw RuntimeException("Failed to download rootfs: ${response.code}")
        
        response.body?.byteStream()?.use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    loadingPopup?.hide()
}
