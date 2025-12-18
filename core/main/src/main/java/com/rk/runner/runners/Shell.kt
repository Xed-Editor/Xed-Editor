package com.rk.runner.runners

import android.content.Context
import com.rk.exec.TerminalCommand
import com.rk.exec.launchInternalTerminal
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.runner.RunnerImpl
import com.rk.utils.errorDialog

class Shell : RunnerImpl() {
    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            errorDialog(msgRes = strings.native_runner)
            return
        }

        launchInternalTerminal(
            context,
            terminalCommand =
                TerminalCommand(
                    sandbox = true,
                    exe = "/bin/bash",
                    args = arrayOf(fileObject.getAbsolutePath()),
                    id = "shell-runner",
                    terminatePreviousSession = true,
                    workingDir = fileObject.getParentFile()?.getAbsolutePath() ?: "/",
                ),
        )
    }

    override fun getName(): String {
        return "Shell Runner"
    }

    override fun getIcon(context: Context): Icon {
        return Icon.DrawableRes(drawables.bash)
    }

    override suspend fun isRunning(): Boolean {
        return false
    }

    override suspend fun stop() {}
}
