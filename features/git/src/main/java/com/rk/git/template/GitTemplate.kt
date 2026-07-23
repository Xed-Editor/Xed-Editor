package com.rk.git.template

import android.app.Activity
import com.rk.file.FileObject
import com.rk.project.ProjectTemplate
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import java.io.File

abstract class GitTemplate(protected val repoUrl: String) : ProjectTemplate {

    abstract val projectName: String
    open val overrideRemote: String? = null

    override fun createProject(
        activity: Activity,
        parentFolder: FileObject,
        onProgress: (Float, String) -> Unit,
        onComplete: (FileObject?) -> Unit,
    ) {
        val monitor =
            object : ProgressMonitor {
                private var cancelled = false

                private var progress = 0f
                private var statusMessage = strings.cloning.getString()

                override fun start(totalTasks: Int) {}

                override fun beginTask(title: String?, totalWork: Int) {
                    val message = title ?: strings.cloning.getString()
                    progress = 0f
                    statusMessage = "$message ($progress/$totalWork)"
                    onProgress(progress, statusMessage)
                }

                override fun update(completed: Int) {
                    progress += completed
                    onProgress(progress, statusMessage)
                }

                override fun endTask() {}

                override fun isCancelled(): Boolean = cancelled || Thread.currentThread().isInterrupted
            }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                onProgress(0f, strings.cloning.getString(activity))

                val projectDir =
                    parentFolder.createChild(false, projectName)
                        ?: throw Exception("Failed to create project directory")

                val localDir = File(projectDir.getAbsolutePath())

                Git.cloneRepository().setURI(repoUrl).setDirectory(localDir).setProgressMonitor(monitor).call().close()

                onProgress(0f, strings.template_cleaning_up_git.getString(activity))
                File(localDir, ".git").deleteRecursively()

                onProgress(0f, strings.template_applying_settings.getString(activity))
                afterClone(projectDir)

                onComplete(projectDir)
            }
                .onFailure {
                    it.printStackTrace()
                    activity.runOnUiThread {
                        strings.template_create_failed.getFilledString(
                            activity,
                            it.message ?: strings.unknown_error.getString(activity),
                        )
                    }
                    onComplete(null)
                }
        }
    }

    open suspend fun afterClone(projectDir: FileObject) {}
}
