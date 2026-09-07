package com.rk.project

import android.content.Intent
import com.rk.DefaultScope
import com.rk.file.FileObject
import com.rk.filetree.FileAction
import com.rk.filetree.FileActionContext
import com.rk.filetree.FileActionType
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.launch

object NewProjectAction : FileAction() {
    override val icon = Icon.ResourceIcon(drawables.folder_managed)
    override val title = strings.new_project.getString()

    override fun action(context: FileActionContext) {
        DefaultScope.launch {
            val intent =
                Intent(context.context, ProjectCreatorActivity::class.java).apply {
                    putExtra("root", context.file.toUri())
                }
            context.context.startActivity(intent)
        }
    }

    override fun isSupported(file: FileObject): Boolean {
        return ProjectTemplateRegistry.categories.any { it.templates.isNotEmpty() }
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}
