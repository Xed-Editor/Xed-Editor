package com.rk.runner.runners

import android.content.Context
import android.graphics.drawable.Drawable
import com.rk.file.FileObject
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.localBinDir
import com.rk.file.localDir
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.application
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.runner.RunnerImpl
import com.rk.terminal.launchInternalTerminal
import com.rk.xededitor.ui.screens.terminal.stat

class UniversalRunner : RunnerImpl(){
    override fun run(context: Context, fileObject: FileObject) {
        with(localBinDir().child("universal_runner")){
            if (exists().not()){
                createFileIfNot()
                writeText(application!!.assets.open("terminal/universal_runner.sh").bufferedReader()
                    .use { it.readText() })
            }
        }

        launchInternalTerminal(context, terminalCommand = TerminalCommand(
            sandbox = true,
            exe = "/bin/bash",
            args = arrayOf(localBinDir().child("universal_runner").absolutePath,fileObject.getAbsolutePath()),
            id = "universal_runner",
            terminatePreviousSession = true,
            workingDir = fileObject.getParentFile()?.getAbsolutePath() ?: "/",
        ))
    }

    override fun getName(): String {
        return "Universal Runner"
    }

    override fun getIcon(context: Context): Drawable? {
        return drawables.run.getDrawable(context)
    }

    override fun isRunning(): Boolean {
        return false
    }

    override fun stop() {

    }

}