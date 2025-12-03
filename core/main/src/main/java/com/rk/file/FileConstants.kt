package com.rk.file

import android.content.Context
import com.rk.utils.application
import java.io.File
import kotlinx.coroutines.runBlocking

fun getPrivateDir(context: Context = application!!): File {
    // blocking thread is fine since we are always know it is just a java.io.File and we are not doing heavy stuff
    return runBlocking { context.filesDir.createDirIfNot().parentFile.createDirIfNot() }
}

fun getCacheDir(context: Context = application!!): File {
    return context.cacheDir.createDirIfNot()
}

fun localDir(context: Context = application!!): File {
    return getPrivateDir(context).child("local").also { it.createDirIfNot() }
}

fun localBinDir(context: Context = application!!): File {
    return localDir(context).child("bin").also { it.createDirIfNot() }
}

fun localLibDir(context: Context = application!!): File {
    return localDir(context).child("lib").also { it.createDirIfNot() }
}

fun sandboxDir(context: Context = application!!): File {
    return localDir(context).child("sandbox").also { it.createDirIfNot() }
}

fun sandboxHomeDir(context: Context = application!!): File {
    return localDir(context).child("home").createDirIfNot()
}

fun runnerDir(context: Context = application!!): File {
    return localDir(context).child("runners").createDirIfNot()
}

fun themeDir(context: Context = application!!): File {
    return localDir(context).child("themes").createDirIfNot()
}

fun persistentTempDir(context: Context = application!!): File {
    return getCacheDir(context).child("tempFiles").createDirIfNot()
}
