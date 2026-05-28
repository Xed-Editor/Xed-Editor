package com.rk.terminal

import android.app.Activity
import android.content.Context
import android.os.Build
import com.rk.XedConstants
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.localLibDir
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

    val prootFile = localBinDir(context).child("proot")
    val tallocFile = localLibDir(context).child("libtalloc.so.2")
    val sandboxFile = File(getTempDir(), "sandbox.tar.gz")

    val abi = Build.SUPPORTED_ABIS
    val isArm64 = abi.contains("arm64-v8a")
    val isX86_64 = abi.contains("x86_64")

    if (!prootFile.exists()) {
        val url = when {
            isX86_64 -> XedConstants.PROOT_X64
            isArm64 -> XedConstants.PROOT_ARM64
            else -> XedConstants.PROOT_ARM
        }
        downloadFile(context, url, prootFile, "proot")
        prootFile.setExecutable(true)
    }

    if (!tallocFile.exists()) {
        val url = when {
            isX86_64 -> XedConstants.TALLOC_X64
            isArm64 -> XedConstants.TALLOC_ARM64
            else -> XedConstants.TALLOC_ARM
        }
        downloadFile(context, url, tallocFile, "libtalloc")
    }

    val rootfsFiles =
        sandboxDir().listFiles()?.filter {
            it.absolutePath != sandboxHomeDir().absolutePath &&
                it.absolutePath != sandboxDir().child("tmp").absolutePath
        } ?: emptyList()

    if (rootfsFiles.isEmpty()) {
        if (!sandboxFile.exists()) {
            val url = when {
                isArm64 -> XedConstants.ROOTFS_ARM64
                isX86_64 -> XedConstants.ROOTFS_X64
                else -> XedConstants.ROOTFS_ARM
            }
            downloadFile(context, url, sandboxFile, "rootfs")
        }
        return NEXT_STAGE.EXTRACTION
    }

    return NEXT_STAGE.NONE
}

private fun downloadFile(context: Context, url: String, outputFile: File, label: String) {
    val activity = context as? AppCompatActivity
    val loadingPopup = activity?.let {
        LoadingPopup(it).setMessage("${strings.downloading.getString()} $label...").show()
    }

    try {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("Failed to download $label: ${response.code}")
            
            response.body?.byteStream()?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    } finally {
        loadingPopup?.hide()
    }
}
