package com.rk.runner.runners

import android.app.Activity
import android.content.Context
import com.rk.TerminalLauncher
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.ProjectRunner
import com.rk.xed.XedManager
import kotlinx.coroutines.runBlocking

object XedProjectRunner : ProjectRunner() {
    override val id: String = "xed_project_runner"
    override val label: String = strings.project_runner.getString()

    override fun getIcon(context: Context): Icon {
        return Icon.ResourceIcon(drawables.run)
    }

    override fun matcher(projectRoot: FileObject): Boolean {
        return runBlocking { XedManager.getRunScript(projectRoot) != null }
    }

    override suspend fun run(activity: Activity, projectRoot: FileObject) {
        val runScript = XedManager.getRunScript(projectRoot) ?: return

        TerminalLauncher.launch(
            activity = activity,
            exe = "/bin/bash",
            args = arrayOf(runScript.getAbsolutePath()),
            id = strings.project_runner.getString(),
            workingDir = projectRoot.getAbsolutePath(),
        )
    }

    override suspend fun isRunning(): Boolean = false

    override suspend fun stop() {}
}
