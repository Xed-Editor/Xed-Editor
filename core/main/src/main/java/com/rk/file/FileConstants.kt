package com.rk.file

import com.rk.libcommons.application
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
        it.createDirIfNot()
    }
}

fun localBinDir(): File {
    return localDir().child("bin").also {
        it.createDirIfNot()
    }
}

fun localLibDir(): File {
    return localDir().child("lib").also {
        it.createDirIfNot()
    }
}

fun sandboxDir(): File {
    return localDir().child("sandbox").also {
        it.createDirIfNot()
    }
}

fun sandboxHomeDir(): File {
    return localDir().child("home").createDirIfNot()
}

fun runnerDir(): File{
    return localDir().child("runners").createDirIfNot()
}