package com.rk.git.template

import android.app.Activity
import com.rk.activities.main.MainActivity
import com.rk.file.FileObject
import com.rk.project.ProjectTemplate
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.jgit.api.Git
import java.io.File

abstract class GitTemplate(private val repoUrl: String) : ProjectTemplate {

    abstract val settings: Map<String, Any>

    override fun createProject(
        activity: Activity,
        parentFolder: FileObject,
        onProgress: (Float, String) -> Unit,
        onComplete: (FileObject?) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                onProgress(0.1f, strings.template_cloning_repository.getString(activity))

                val name = settings["name"] as String
                val projectDir =
                    parentFolder.createChild(false, name) ?: throw Exception("Failed to create project directory")

                val localDir = File(projectDir.getAbsolutePath())

                Git.cloneRepository().setURI(repoUrl).setDirectory(localDir).call()

                onProgress(0.7f, strings.template_cleaning_up_git.getString(activity))
                File(localDir, ".git").deleteRecursively()

                onProgress(0.8f, strings.template_applying_settings.getString(activity))
                afterClone(projectDir)

                activity.runOnUiThread {
                    strings.template_create_success.getFilledString(activity, label)
                    MainActivity.instance?.drawerViewModel?.addFileTreeTab(projectDir, true)
                }
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
