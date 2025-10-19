package com.rk.runner.runners

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Environment
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.localBinDir
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.application
import com.rk.libcommons.dialog
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.RunnerImpl
import com.rk.terminal.launchInternalTerminal

class UniversalRunner : RunnerImpl() {
    @SuppressLint("SdCardPath")
    override fun run(context: Context, fileObject: FileObject) {
        with(localBinDir().child("universal_runner")) {
            if (exists().not()) {
                createFileIfNot()
                writeText(
                    application!!.assets.open("terminal/universal_runner.sh").bufferedReader()
                        .use { it.readText() }
                )
            }
        }

        if (fileObject !is FileWrapper) {
            dialog(
                title = strings.attention.getString(),
                msg = strings.non_native_filetype.getString(),
                onOk = {}
            )
            return
        }

        val path = fileObject.getAbsolutePath()
        if (
            path.startsWith("/sdcard") ||
            path.startsWith("/storage/") ||
            path.startsWith(Environment.getExternalStorageDirectory().absolutePath)
        ) {
            dialog(
                title = strings.attention.getString(),
                msg = strings.sdcard_filetype.getString(),
                okString = strings.continue_action,
                onCancel = {},
                onOk = { launchUniversalRunner(context, fileObject) },
            )
            return
        }

        launchUniversalRunner(context, fileObject)
    }

    fun launchUniversalRunner(context: Context, fileObject: FileObject) {
        launchInternalTerminal(
            context, terminalCommand = TerminalCommand(
                sandbox = true,
                exe = "/bin/bash",
                args = arrayOf(
                    localBinDir().child("universal_runner").absolutePath,
                    fileObject.getAbsolutePath()
                ),
                id = "universal_runner",
                terminatePreviousSession = true,
                workingDir = fileObject.getParentFile()?.getAbsolutePath() ?: "/",
            )
        )
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