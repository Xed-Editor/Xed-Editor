package com.rk.activities.main.session

import androidx.lifecycle.viewModelScope
import com.rk.activities.main.MainActivity
import com.rk.file.FileObject
import com.rk.tabs.base.Tab
import com.rk.tabs.base.TabRegistry
import kotlinx.coroutines.launch
import java.io.Serializable

sealed interface TabState : Serializable {
    suspend fun toTab(): Tab?
}

data class EditorTabState(
    val fileObject: FileObject?,
    val projectRoot: FileObject?,
    val scopeRoot: FileObject?,
    val content: String?,
    val isDirty: Boolean = false,
    val isReadOnly: Boolean = false,
    val customTitle: String? = null,
    val fallbackExtension: String = "txt",
) : TabState {
    override suspend fun toTab(): Tab? {
        if (fileObject != null && !fileObject.exists() && !fileObject.canRead()) return null

        return MainActivity.instance?.viewModel?.run {
            val editorTab =
                editorManager.createEditorTab(
                    file = fileObject,
                    projectRoot = projectRoot,
                    scopeRoot = scopeRoot,
                    isReadOnly = isReadOnly,
                    customTitle = customTitle,
                    fallbackExtension = fallbackExtension,
                    initialContent = content,
                )

            viewModelScope.launch {
                editorTab.editorState.contentRendered.await()
                editorTab.editorState.isDirty = isDirty
            }

            editorTab
        }
    }
}


data class FileTabState(val fileObject: FileObject, val projectRoot: FileObject?, val scopeRoot: FileObject?) : TabState {
    override suspend fun toTab() =
        TabRegistry.getTab(
            file = fileObject,
            projectRoot = projectRoot,
            scopeRoot = scopeRoot,
            viewModel = MainActivity.instance!!.viewModel,
            readOnly = false,
            customTitle = null,
        )
}

/**
 * A tab state owned by a feature that lives outside the core, carried as opaque [payload] bytes.
 *
 * [type] is the key the owning feature registered with [PayloadTabRegistry], which decides how the
 * bytes are written and read. [projectRoot] and [scopeRoot] are kept here rather than inside the
 * payload because every tab has them, so restoring a payload tab needs no cooperation from the
 * feature to land in the right project scope.
 */
data class PayloadTabState(
    val type: String,
    val payload: ByteArray,
    val projectRoot: FileObject?,
    val scopeRoot: FileObject?,
) : TabState {
    // ByteArray is compared by identity, so data-class equality has to be spelled out; otherwise two
    // identical states would look different (and an empty payload would compare equal to any other
    // empty array only by luck).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PayloadTabState) return false
        return type == other.type &&
            payload.contentEquals(other.payload) &&
            projectRoot == other.projectRoot &&
            scopeRoot == other.scopeRoot
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (projectRoot?.hashCode() ?: 0)
        result = 31 * result + (scopeRoot?.hashCode() ?: 0)
        return result
    }

    override suspend fun toTab(): Tab? =
        PayloadTabRegistry.restore(type, payload)?.also {
            it.projectRoot = projectRoot
            it.scopeRoot = scopeRoot
        }
}
