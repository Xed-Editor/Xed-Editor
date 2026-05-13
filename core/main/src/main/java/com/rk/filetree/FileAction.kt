package com.rk.filetree

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.lifecycle.viewModelScope
import com.rk.activities.main.MainActivity
import com.rk.activities.terminal.Terminal
import com.rk.file.FileObject
import com.rk.file.FileOperations
import com.rk.file.FileWrapper
import com.rk.icons.CreateNewFile
import com.rk.icons.CreateNewFolder
import com.rk.icons.Icon
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.app.InbuiltFeatures
import com.rk.tabs.editor.EditorTab
import com.rk.utils.showTerminalNotice
import com.rk.utils.toast
import kotlinx.coroutines.launch

data class FileActionContext(
    val file: FileObject,
    val root: FileObject?,
    val viewModel: FileTreeViewModel,
    val context: Context,
)

data class MultiFileActionContext(
    val files: List<FileObject>,
    val root: FileObject?,
    val viewModel: FileTreeViewModel,
    val context: Context,
)

data class FileActionType(val file: Boolean, val folder: Boolean, val rootFolder: Boolean) {
    companion object {
        val All = FileActionType(file = true, folder = true, rootFolder = true)
    }
}

interface BaseFileAction {
    val icon: Icon
    val title: String
    val type: FileActionType
    val importance: Int
}

abstract class FileAction : BaseFileAction {
    abstract override val icon: Icon
    abstract override val title: String

    abstract fun action(context: FileActionContext)

    open fun isSupported(file: FileObject): Boolean = true

    open fun isEnabled(file: FileObject): Boolean = true

    abstract override val type: FileActionType
    override val importance = 0
}

abstract class MultiFileAction : BaseFileAction {
    abstract override val icon: Icon
    abstract override val title: String

    abstract fun action(context: MultiFileActionContext)

    open fun isSupported(files: List<FileObject>): Boolean = true

    open fun isEnabled(files: List<FileObject>): Boolean = true

    abstract override val type: FileActionType
    override val importance = 0
}

object CloseAction : FileAction() {
    override val icon = Icon.VectorIcon(Icons.Outlined.Close)
    override val title = strings.close.getString()

    override fun action(context: FileActionContext) = context.viewModel.showCloseProjectConfirmation(context.file)

    override val type = FileActionType(file = false, folder = false, rootFolder = true)
}

object RefreshAction : MultiFileAction() {
    override val icon = Icon.VectorIcon(Icons.Outlined.Refresh)
    override val title = strings.refresh.getString()

    override fun action(context: MultiFileActionContext) {
        context.files.forEach { context.viewModel.updateCache(it) }
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}

object TerminalAction : FileAction() {
    override val icon = Icon.DrawableRes(drawables.terminal)
    override val title = strings.open_in_terminal.getString()

    override fun action(context: FileActionContext) {
        val file = context.file
        val context = context.context

        showTerminalNotice(activity = MainActivity.instance!!) {
            val intent = Intent(context, Terminal::class.java)
            intent.putExtra("cwd", file.getAbsolutePath())
            context.startActivity(intent)
        }
    }

    override fun isSupported(file: FileObject): Boolean {
        return file is FileWrapper && InbuiltFeatures.terminal.state.value
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}

object CreateNewFileAction : FileAction() {
    override val icon = Icon.VectorIcon(XedIcons.CreateNewFile)
    override val title = strings.new_file.getString()

    override fun action(context: FileActionContext) {
        context.viewModel.showCreateDialog(true, context.file, context.root)
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}

object CreateNewFolderAction : FileAction() {
    override val icon = Icon.VectorIcon(XedIcons.CreateNewFolder)
    override val title = strings.new_folder.getString()

    override fun action(context: FileActionContext) {
        context.viewModel.showCreateDialog(false, context.file, context.root)
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}

object RenameAction : FileAction() {
    override val icon = Icon.VectorIcon(Icons.Outlined.Edit)
    override val title = strings.rename.getString()

    override fun action(context: FileActionContext) {
        context.viewModel.showRenameDialog(context.file)
    }

    override val type = FileActionType.All
}

object DeleteAction : MultiFileAction() {
    override val icon = Icon.VectorIcon(Icons.Outlined.Delete)
    override val title = strings.delete.getString()

    override fun action(context: MultiFileActionContext) {
        context.viewModel.showDeleteConfirmation(context.files, context.root)
    }

    override val type = FileActionType.All
    override val importance = 3
}

object CopyAction : MultiFileAction() {
    override val icon = Icon.DrawableRes(drawables.copy)
    override val title = strings.copy.getString()

    override fun action(context: MultiFileActionContext) {
        FileOperations.copyToClipboard(context.files)
        toast(context.context.getString(strings.copied))
    }

    override val type = FileActionType.All
    override val importance = 1
}

object CutAction : MultiFileAction() {
    override val icon = Icon.DrawableRes(drawables.cut)
    override val title = strings.cut.getString()

    override fun action(context: MultiFileActionContext) {
        FileOperations.copyToClipboard(context.files, isCut = true)
        context.files.forEach { context.viewModel.markNodeAsCut(it) }
    }

    override val type = FileActionType.All
}

object PasteAction : FileAction() {
    override val icon = Icon.DrawableRes(drawables.paste)
    override val title = strings.paste.getString()

    override fun action(context: FileActionContext) {
        context.viewModel.viewModelScope.launch {
            val isCut = FileOperations.isCut
            val clipboardFiles = FileOperations.clipboard

            context.viewModel.withFileOperation {
                for (clipboardFile in clipboardFiles) {
                    FileOperations.pasteFile(
                            context = context.context,
                            sourceFile = clipboardFile,
                            destinationFolder = context.file,
                            isCut = isCut,
                        )
                        .onFailure { toast(it.message ?: strings.paste_failed.getString()) }
                        .onSuccess {
                            if (isCut) {
                                MainActivity.instance?.apply {
                                    val targetTab =
                                        viewModel.tabs.find { it is EditorTab && it.file == clipboardFile }
                                            as? EditorTab
                                    targetTab?.file = context.file.getChildForName(clipboardFile.getName())
                                }
                            }
                            clipboardFile.getParentFile()?.let { context.viewModel.updateCache(it) }
                            context.viewModel.updateCache(context.file)
                            context.viewModel.unmarkNodeAsCut(clipboardFile)
                        }
                }
            }
        }
    }

    override fun isEnabled(file: FileObject): Boolean {
        return FileOperations.clipboard.isNotEmpty()
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
    override val importance: Int
        get() = if (FileOperations.clipboard.isEmpty()) super.importance else 2
}

object OpenWithAction : FileAction() {
    override val icon = Icon.DrawableRes(drawables.open_in_new)
    override val title = strings.open_with.getString()

    override fun action(context: FileActionContext) {
        context.viewModel.viewModelScope.launch { FileOperations.openWithExternalApp(context.context, context.file) }
    }

    override val type = FileActionType.All
}

object SaveAsAction : FileAction() {
    override val icon = Icon.DrawableRes(drawables.file_symlink)
    override val title = strings.save_as.getString()

    override fun action(context: FileActionContext) {
        FileOperations.saveAs(context.file)
    }

    override val type = FileActionType.All
}

object AddFileAction : FileAction() {
    override val icon = Icon.DrawableRes(drawables.arrow_downward)
    override val title = strings.add_file.getString()

    override fun action(context: FileActionContext) {
        FileOperations.addFile(context.file)
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}

object OpenAsProjectAction : FileAction() {
    override val icon = Icon.DrawableRes(drawables.folder_code)
    override val title = strings.open_as_project.getString()

    override fun action(context: FileActionContext) {
        addProject(context.file, true)
    }

    override fun isEnabled(file: FileObject): Boolean {
        return drawerTabs.none { it is FileTreeTab && it.root == file }
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}

object PropertiesAction : FileAction() {
    override val icon = Icon.VectorIcon(Icons.Outlined.Info)
    override val title = strings.properties.getString()

    override fun action(context: FileActionContext) {
        context.viewModel.showPropertiesDialog(context.file)
    }

    override val type = FileActionType.All
}
