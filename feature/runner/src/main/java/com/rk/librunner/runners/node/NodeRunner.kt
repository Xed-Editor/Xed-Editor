package com.rk.librunner.runners.node

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.librunner.RunnerImpl
import com.rk.librunner.commonUtils
import com.rk.librunner.commonUtils.exctractAssets
import com.rk.librunner.commonUtils.runCommand
import java.io.File

class NodeRunner : RunnerImpl {
    override fun run(file: File, context: Context) {
        exctractAssets(context){
            runCommand(
                alpine = true,
                shell = "/karbon/rootfs/nodejs.sh",
                args = arrayOf(file.name),
                workingDir = file.parentFile!!.absolutePath,
                context = context
            )
        }

    }

    override fun getName(): String {
        return "Nodejs"
    }

    override fun getDescription(): String {
        return "Nodejs v20"
    }

    override fun getIcon(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, com.rk.libcommons.R.drawable.ic_language_js)
    }
}