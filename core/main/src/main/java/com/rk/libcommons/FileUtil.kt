package com.rk.libcommons

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.rk.file_wrapper.FileObject
import com.rk.file_wrapper.FileWrapper
import com.rk.file_wrapper.UriWrapper
import com.rk.libcommons.PathUtils.toPath
import java.io.File

fun localDir(): File {
    return File(application!!.filesDir.parentFile, "local").also {
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


fun File.child(fileName:String):File {
    return File(this,fileName)
}

fun File.createFileIfNot():File{
    if (parentFile.exists().not()){
        parentFile.mkdirs()
    }
    if (exists().not()){
        createNewFile()
    }
    return this
}

fun File.toFileWrapper():FileWrapper{
    return FileWrapper(this)
}

fun Uri.toFileObject():FileObject{
    val path = toPath()
    val allowFileWrapper = (path.contains(Environment.getExternalStorageDirectory().absolutePath).not() && path.contains("/sdcard").not()) || ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) && Environment.isExternalStorageManager())
    if (allowFileWrapper){
        if (!toString().startsWith("content://com.termux")){
            val file = File(path)
            if (file.exists() && file.canRead() && file.canWrite()){
                return FileWrapper(file)
            }
        }

    }
    return UriWrapper(this)
}

fun String.toFileObject():FileObject{
    return Uri.parse(this).toFileObject()
}