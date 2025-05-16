package com.rk.libcommons

import android.net.Uri
import android.os.Build
import android.os.Environment
import com.rk.file_wrapper.FileObject
import com.rk.file_wrapper.FileWrapper
import com.rk.file_wrapper.UriWrapper
import com.rk.libcommons.PathUtils.toPath
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

fun uriToFileObject(uri: Uri, expectFile: Boolean? = null): FileObject {
    val path = uri.toPath()

    val file = File(path)

    val condition = when (expectFile) {
        null -> {
            true
        }

        true -> {
            file.isFile
        }

        else -> {
            file.isDirectory
        }
    }

    if (file.exists() && file.canRead() && file.canWrite() && condition) {
        return FileWrapper(file)
    }

    return UriWrapper(uri)
}