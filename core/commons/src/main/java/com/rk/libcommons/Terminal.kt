package com.rk.libcommons

import android.content.Context
import java.io.File

fun Context.localDir(): File {
    return File(filesDir.parentFile, "local").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun Context.localBinDir(): File {
    return File(filesDir.parentFile, "local/bin").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun Context.localLibDir(): File {
    return File(filesDir.parentFile, "local/lib").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun Context.alpineDir(): File {
    return File(localDir(),"alpine").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}