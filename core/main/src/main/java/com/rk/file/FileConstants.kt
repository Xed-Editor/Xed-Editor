package com.rk.file

import android.content.Context
import com.rk.libcommons.application
import com.rk.xededitor.BuildConfig
import java.io.File

fun getPrivateDir(context: Context = application!!): File {
    return context.filesDir.parentFile!!
}

fun localDir(context: Context = application!!): File {
    return getPrivateDir(context).child("local").also {
        it.createDirIfNot()
    }
}

fun localBinDir(context: Context = application!!): File {
    return localDir(context).child("bin").also {
        it.createDirIfNot()
    }
}

fun localLibDir(context: Context = application!!): File {
    return localDir(context).child("lib").also {
        it.createDirIfNot()
    }
}

fun sandboxDir(context: Context = application!!): File {
    return localDir(context).child("sandbox").also {
        it.createDirIfNot()
    }
}

fun sandboxHomeDir(context: Context = application!!): File {
    return localDir(context).child("home").createDirIfNot()
}

fun runnerDir(context: Context = application!!): File{
    return localDir(context).child("runners").createDirIfNot()
}