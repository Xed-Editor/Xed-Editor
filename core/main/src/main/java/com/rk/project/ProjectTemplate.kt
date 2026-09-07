package com.rk.project

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.rk.extension.api.XedExtensionPoint
import com.rk.file.FileObject
import com.rk.icons.Icon

interface ProjectTemplate {
    val id: String
    val label: String
    val description: String?
    val icon: Icon
    val size: Long?
    val validConfiguration: Boolean

    suspend fun createProject(
        activity: Activity,
        parentFolder: FileObject,
        onProgress: (Float?, String) -> Unit,
        onComplete: (FileObject?) -> Unit,
    )

    @Composable fun Configuration() {}
}

class ProjectCategory(val id: String, val label: String, val icon: Icon? = null) {
    val templates = mutableStateListOf<ProjectTemplate>()
}

object ProjectTemplateRegistry {
    private val _categories = mutableStateListOf<ProjectCategory>()
    val categories: SnapshotStateList<ProjectCategory> = _categories

    @XedExtensionPoint
    fun registerCategory(category: ProjectCategory) {
        if (_categories.none { it.id == category.id }) {
            _categories.add(category)
        }
    }

    @XedExtensionPoint
    fun registerTemplate(category: ProjectCategory, template: ProjectTemplate) {
        if (category.templates.none { it.id == template.id }) {
            category.templates.add(template)
        }
    }

    @XedExtensionPoint
    fun getCategoryForId(id: String): ProjectCategory? {
        return _categories.find { it.id == id }
    }

    @XedExtensionPoint
    fun getTemplateForId(category: ProjectCategory, templateId: String): ProjectTemplate? {
        return category.templates.find { it.id == templateId }
    }

    @XedExtensionPoint
    fun unregisterTemplate(category: ProjectCategory, template: ProjectTemplate) {
        category.templates.remove(template)
    }

    @XedExtensionPoint
    fun unregisterCategory(category: ProjectCategory) {
        _categories.remove(category)
    }
}
