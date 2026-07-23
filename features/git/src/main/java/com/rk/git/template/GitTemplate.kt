package com.rk.git.template

import android.app.Activity
import com.rk.file.FileObject
import com.rk.project.ProjectTemplate
import com.rk.resources.getString
import com.rk.resources.strings
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import java.io.File

abstract class GitTemplate(protected val repoUrl: String) : ProjectTemplate {

    abstract val projectName: String
    open val overrideRemote: String? = null

    override suspend fun createProject(
        activity: Activity,
        parentFolder: FileObject,
        onProgress: (Float?, String) -> Unit,
        onComplete: (FileObject?) -> Unit,
    ) {
        val monitor =
            object : ProgressMonitor {
                private var cancelled = false

                private var progress = 0
                private var maxProgress = 0
                private var statusMessage = strings.cloning.getString()

                override fun start(totalTasks: Int) {}

                override fun beginTask(title: String?, totalWork: Int) {
                    progress = 0
                    maxProgress = totalWork
                    statusMessage = title ?: strings.cloning.getString()
                    publishProgress()
                }

                override fun update(completed: Int) {
                    progress += completed
                    publishProgress()
                }

                private fun publishProgress() {
                    onProgress(
                        if (maxProgress > 0) progress.toFloat() / maxProgress else 0f,
                        "$statusMessage ($progress/$maxProgress)",
                    )
                }

                override fun endTask() {}

                override fun isCancelled(): Boolean = cancelled || Thread.currentThread().isInterrupted
            }

        onProgress(null, strings.cloning.getString(activity))

        val projectDir =
            parentFolder.createChild(false, projectName) ?: throw Exception("Failed to create project directory")

        val localDir = File(projectDir.getAbsolutePath())

        Git.cloneRepository().setURI(repoUrl).setDirectory(localDir).setProgressMonitor(monitor).call().close()

        onProgress(null, strings.template_cleaning_up_git.getString(activity))
        File(localDir, ".git").deleteRecursively()

        onProgress(null, strings.template_applying_settings.getString(activity))
        afterClone(projectDir)

        onComplete(projectDir)
    }

    open suspend fun afterClone(projectDir: FileObject) {}
}
