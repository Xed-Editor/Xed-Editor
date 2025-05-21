package com.rk.libcommons

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.rk.file_wrapper.FileObject
import com.rk.file_wrapper.FileWrapper
import com.rk.file_wrapper.UriWrapper
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.BuildConfig
import java.io.File

fun getPrivateDir(): File {
    return if (application == null) {
        if (BuildConfig.DEBUG) {
            File("/data/data/com.rk.xededitor.debug").also {
                if (it.exists().not() || it.canRead().not() || it.canWrite().not()) {
                    throw RuntimeException("Private file path $it is not accessible")
                }
            }
        } else {
            File("/data/data/com.rk.xededitor").also {
                if (it.exists().not() || it.canRead().not() || it.canWrite().not()) {
                    throw RuntimeException("Private file path $it is not accessible")
                }
            }
        }
    } else {
        application!!.filesDir.parentFile!!
    }
}

fun localDir(): File {
    return getPrivateDir().child("local").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun localBinDir(): File {
    return localDir().child("bin").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun localLibDir(): File {
    return localDir().child("lib").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun alpineDir(): File {
    return localDir().child("alpine").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun alpineHomeDir(): File {
    return alpineDir().child("root").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}


fun File.child(fileName: String): File {
    return File(this, fileName)
}

fun File.createFileIfNot(): File {
    if (parentFile.exists().not()) {
        parentFile.mkdirs()
    }
    if (exists().not()) {
        createNewFile()
    }
    return this
}

fun File.toFileWrapper(): FileWrapper {
    return FileWrapper(this)
}

inline fun isFileManager(): Boolean {
    return ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) && Environment.isExternalStorageManager())
}

fun openWith(context: Context, file: FileObject) {

    try {
        val uri: Uri = when (file) {
            is UriWrapper -> {
                file.toUri()
            }

            is FileWrapper -> {
                FileProvider.getUriForFile(
                    context,
                    context.applicationContext.packageName + ".fileprovider",
                    file.file,
                )
            }

            else -> {
                throw RuntimeException("Unsupported FileObject")
            }
        }

        val mimeType = file.getMimeType(context)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, strings.canthandle.getString(), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        toast(strings.file_open_denied.getString())
    }
}