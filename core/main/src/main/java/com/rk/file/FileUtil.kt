package com.rk.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.toast
import java.io.File
import java.util.zip.ZipFile

fun File.child(fileName: String): File {
    return File(this, fileName)
}

fun File.createFileIfNot(): File {
    if (parentFile?.exists()?.not() == true) {
        parentFile!!.mkdirs()
    }
    if (exists().not()) {
        createNewFile()
    }
    return this
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

fun File.createDirIfNot(): File {
    if (exists().not()) {
        mkdirs()
    }
    return this
}

suspend fun FileObject.createDirIfNot(): FileObject {
    if (exists().not()) {
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

/**
 * Unzips the current file to the specified destination directory.
 *
 * @param destDir The directory where the contents of the zip file will be extracted.
 */
fun File.unzipTo(destDir: File) {
    if (!destDir.exists()) {
        destDir.mkdirs()
    }
    ZipFile(this).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            val target = File(destDir, entry.name)
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

suspend fun openWith(context: Context, file: FileObject) {
    try {
        val uri: Uri =
            when (file) {
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

        val intent =
            Intent(Intent.ACTION_VIEW).apply {
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
