package com.rk.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import java.io.File

fun File.child(fileName: String): File {
    return File(this, fileName)
}

suspend fun File.createFileIfNot(): File {
    return (FileWrapper(this).createFileIfNot() as FileWrapper).file
}

suspend fun FileObject.createFileIfNot(): FileObject {
    if (getParentFile()?.exists()?.not() == true) {
        getParentFile()!!.mkdirs()
    }
    if (exists().not()) {
        createNewFile()
    }
    return this
}



suspend fun FileObject.createDirIfNot(): FileObject {
    if (exists().not()){
        mkdirs()
    }
    return this
}

fun File.createDirIfNot(): File {
    if (exists().not()){
        mkdirs()
    }
    return this
}

fun File.toFileWrapper(): FileWrapper {
    return FileWrapper(this)
}

inline fun isFileManager(): Boolean {
    return ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) && Environment.isExternalStorageManager())
}

suspend fun openWith(context: Context, file: FileObject) {
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
            Toast.makeText(context, strings.cant_handle.getString(), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        toast(strings.file_open_denied.getString())
    }
}