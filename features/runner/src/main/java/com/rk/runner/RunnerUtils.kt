package com.rk.runner

import android.content.Context
import com.rk.file.localDir
import com.rk.utils.application
import java.io.File
import com.rk.file.child
import com.rk.file.createDirIfNot

fun runnerDir(context: Context = application!!): File {
    return localDir(context).child("runners").createDirIfNot()
}
