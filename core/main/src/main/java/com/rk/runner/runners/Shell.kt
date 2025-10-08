package com.rk.runner.runners

import android.R
import android.content.Context
import android.graphics.drawable.Drawable
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.application
import com.rk.libcommons.errorDialog
import com.rk.libcommons.toast
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.strings
import com.rk.runner.RunnerImpl
import com.rk.terminal.launchInternalTerminal

class Shell : RunnerImpl(){
    override suspend fun run(context: Context, fileObject: FileObject) {
        if(fileObject !is FileWrapper){
            errorDialog(msgRes = strings.native_runner)
            return
        }


        launchInternalTerminal(context, terminalCommand = TerminalCommand(
            sandbox = true,
            exe = "/bin/bash",
            args = arrayOf(fileObject.getAbsolutePath()),
            id = "shell-runner",
            terminatePreviousSession = true,
            workingDir = fileObject.getParentFile()?.getAbsolutePath() ?: "/",
        ))
    }

    override suspend fun getName(): String {
        return "Shell Runner"
    }

    override suspend fun getIcon(context: Context): Drawable? {
        return drawables.bash.getDrawable(context)
    }

    override suspend fun isRunning(): Boolean {
        return false
    }

    override suspend fun stop() {

    }

}