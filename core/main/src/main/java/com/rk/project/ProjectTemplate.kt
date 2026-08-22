package com.rk.project

import android.app.Activity
import androidx.compose.runtime.Composable
import com.rk.extension.api.XedExtensionPoint
import com.rk.file.FileObject
import com.rk.icons.Icon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    val templates = mutableListOf<ProjectTemplate>()
}

object ProjectTemplateRegistry {
    private val _categories = MutableStateFlow<List<ProjectCategory>>(emptyList())
    val categories: StateFlow<List<ProjectCategory>> = _categories.asStateFlow()

    @XedExtensionPoint
    fun registerCategory(category: ProjectCategory) {
        _categories.update { list -> if (list.none { it.id == category.id }) list + category else list }
    }

    @XedExtensionPoint
    fun registerTemplate(category: ProjectCategory, template: ProjectTemplate) {
        if (category.templates.none { it.id == template.id }) {
            category.templates.add(template)
        }
    }

    @XedExtensionPoint
    fun getCategoryForId(id: String): ProjectCategory? {
        return _categories.value.find { it.id == id }
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
        _categories.update { it - category }
    }
}
