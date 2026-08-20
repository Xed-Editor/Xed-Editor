package com.rk.filetree

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.lifecycle.viewModelScope
import com.rk.activities.main.MainActivity
import com.rk.drawer.DrawerViewModel
import com.rk.extension.ActivityProvider
import com.rk.extension.api.IntentHandleRegistry
import com.rk.file.FileObject
import com.rk.file.FileOperations
import com.rk.file.FileWrapper
import com.rk.file.unzipTo
import com.rk.icons.CreateNewFile
import com.rk.icons.CreateNewFolder
import com.rk.icons.Icon
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.tabs.editor.EditorTab
import com.rk.utils.LoadingPopup
import com.rk.utils.logError
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.seconds

data class FileActionContext(
    val file: FileObject,
    val root: FileObject?,
    val viewModel: FileTreeViewModel,
    val drawerViewModel: DrawerViewModel,
    val context: Context,
)

data class MultiFileActionContext(
    val files: List<FileObject>,
    val root: FileObject?,
    val viewModel: FileTreeViewModel,
    val drawerViewModel: DrawerViewModel,
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

    suspend fun execute(context: FileActionContext) {
        action(context)

    }

    protected abstract suspend fun action(context: FileActionContext)

    open suspend fun isSupported(
        file: FileObject,
        root: FileObject?
    ): Boolean = true

    open suspend fun isEnabled(
        file: FileObject,
        root: FileObject?
    ): Boolean = true

    abstract override val type: FileActionType
    override val importance = 0
}

abstract class MultiFileAction : BaseFileAction {
    abstract override val icon: Icon
    abstract override val title: String

    suspend fun execute(context: MultiFileActionContext) {
        action(context)
    }

    protected abstract suspend fun action(context: MultiFileActionContext)

    open suspend fun isSupported(
        files: List<FileObject>,
        root: FileObject?
    ): Boolean = true

    open suspend fun isEnabled(
        files: List<FileObject>,
        root: FileObject?
    ): Boolean = true

    abstract override val type: FileActionType
    override val importance = 0
}

object CloseAction : FileAction() {
    override val icon = Icon.VectorIcon(Icons.Outlined.Close)
    override val title = strings.close.getString()

    override suspend fun action(context: FileActionContext) = context.viewModel.showCloseProjectConfirmation(context.file)

    override val type = FileActionType(file = false, folder = false, rootFolder = true)
}

object RefreshAction : MultiFileAction() {
    override val icon = Icon.VectorIcon(Icons.Outlined.Refresh)
    override val title = strings.refresh.getString()

    override suspend fun action(context: MultiFileActionContext) {
        context.files.forEach { context.viewModel.updateCache(it) }
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}

object CreateNewFileAction : FileAction() {
    override val icon = Icon.VectorIcon(XedIcons.CreateNewFile)
    override val title = strings.new_file.getString()

    override suspend fun action(context: FileActionContext) {
        context.viewModel.showCreateDialog(true, context.file, context.root)
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}

object CreateNewFolderAction : FileAction() {
    override val icon = Icon.VectorIcon(XedIcons.CreateNewFolder)
    override val title = strings.new_folder.getString()

    override suspend fun action(context: FileActionContext) {
        context.viewModel.showCreateDialog(false, context.file, context.root)
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}

object RenameAction : FileAction() {
    override val icon = Icon.VectorIcon(Icons.Outlined.Edit)
    override val title = strings.rename.getString()

    override suspend fun action(context: FileActionContext) {
        context.viewModel.showRenameDialog(context.file)
    }

    override val type = FileActionType.All
}

object DeleteAction : MultiFileAction() {
    override val icon = Icon.VectorIcon(Icons.Outlined.Delete)
    override val title = strings.delete.getString()

    override suspend fun action(context: MultiFileActionContext) {
        context.viewModel.showDeleteConfirmation(context.files, context.root)
    }



    /*
    * Prevent total stupidity
    * Some idiots delete their whole storage
    * In this function we have to determine if this directory is protected or not like root of the internal storage or sdcard
    * */
    private suspend fun isProtected(fileObject: FileObject): Boolean{
        @SuppressLint("SdCardPath")
        fun switchProtected(path: String): Boolean{
            return when(path){
                Environment.getExternalStorageDirectory().path -> true
                Environment.getExternalStorageDirectory().absolutePath -> true
                Environment.getExternalStorageDirectory().canonicalPath -> true
                "/sdcard" -> true
                "/storage" -> true
                "/storage/emulated/0" -> true
                "content://com.android.externalstorage.documents/tree/primary%3A" -> true
                else -> false
            }
        }

        //Don't allow deletion of folders with these names
        val protectedName = when(fileObject.getName()){
            "DCIM" -> true
            else -> false
        }

        if (protectedName){
            return true
        }

        return if (fileObject is FileWrapper){
            switchProtected(fileObject.getCanonicalPath()) || switchProtected(fileObject.getAbsolutePath())
        }else{
            switchProtected(fileObject.toUri().toString())
        }
    }


    override suspend fun isEnabled(files: List<FileObject>,root: FileObject?): Boolean {
        val deletingProtected = files.any {
            isProtected(it)
        }

        return deletingProtected.not()
    }

    override val type = FileActionType.All
    override val importance = 0
}

object CopyAction : MultiFileAction() {
    override val icon = Icon.ResourceIcon(drawables.copy)
    override val title = strings.copy.getString()

    override suspend fun action(context: MultiFileActionContext) {
        FileOperations.copyToClipboard(context.files)
        toast(context.context.getString(strings.copied))
    }

    override val type = FileActionType.All
    override val importance = 1
}

object CutAction : MultiFileAction() {
    override val icon = Icon.ResourceIcon(drawables.cut)
    override val title = strings.cut.getString()

    override suspend fun action(context: MultiFileActionContext) {
        FileOperations.copyToClipboard(context.files, isCut = true)
        context.files.forEach { context.viewModel.markNodeAsCut(it) }
    }

    override val type = FileActionType.All
}

object PasteAction : FileAction() {
    override val icon = Icon.ResourceIcon(drawables.paste)
    override val title = strings.paste.getString()

    override suspend fun action(context: FileActionContext) {
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
                                    targetTab?.file = context.file.getChild(clipboardFile.getName())
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

    override suspend fun isEnabled(file: FileObject, root: FileObject?): Boolean {
        return FileOperations.clipboard.isNotEmpty()
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
    override val importance: Int
        get() = if (FileOperations.clipboard.isEmpty()) super.importance else 2
}

object OpenWithAction : FileAction() {
    override val icon = Icon.ResourceIcon(drawables.open_in_new)
    override val title = strings.open_with.getString()

    override suspend fun action(context: FileActionContext) {
        context.viewModel.viewModelScope.launch { FileOperations.openWithExternalApp(context.context, context.file) }
    }

    override val type = FileActionType.All
}

object SaveAsAction : FileAction() {
    override val icon = Icon.ResourceIcon(drawables.file_symlink)
    override val title = strings.save_as.getString()

    override suspend fun action(context: FileActionContext) {
        FileOperations.saveAs(context.file)
    }

    override val type = FileActionType.All
}

object AddFileAction : FileAction() {
    override val icon = Icon.ResourceIcon(drawables.arrow_downward)
    override val title = strings.add_file.getString()

    override suspend fun action(context: FileActionContext) {
        FileOperations.addFile(context.file)
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}

object OpenAsProjectAction : FileAction() {
    override val icon = Icon.ResourceIcon(drawables.folder_code)
    override val title = strings.open_as_project.getString()

    override suspend fun action(context: FileActionContext) {
        context.drawerViewModel.addFileTreeTab(context.file, true)
    }

    override suspend fun isEnabled(file: FileObject, root: FileObject?): Boolean {
        val drawerViewModel = MainActivity.instance?.drawerViewModel ?: return false
        return drawerViewModel.drawerTabs.none { it is FileTreeTab && it.root == file }
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}

object PropertiesAction : FileAction() {
    override val icon = Icon.VectorIcon(Icons.Outlined.Info)
    override val title = strings.properties.getString()

    override suspend fun action(context: FileActionContext) {
        context.viewModel.showPropertiesDialog(context.file)
    }

    override val type = FileActionType.All
}

object UnzipAction : FileAction() {
    override val icon = Icon.ResourceIcon(drawables.archive)
    override val title = strings.unzip.getString()

    override suspend fun action(context: FileActionContext) {
        context.viewModel.viewModelScope.launch(Dispatchers.IO) {
            val zipFile = File(context.file.getAbsolutePath())
            val targetDir = File(zipFile.parentFile, zipFile.nameWithoutExtension).apply { mkdirs() }

            runCatching {
                context.viewModel.withFileOperation {
                    zipFile.unzipTo(targetDir)
                }
            }
                .onSuccess {
                    val parent = context.file.getParentFile()
                    parent?.let { context.viewModel.updateCache(it) }
                    toast(strings.unzip_success.getString())
                }
                .onFailure { e ->
                    logError(e)
                    toast(strings.unzip_failed.getFilledString(e.message))
                }
        }
    }

    override suspend fun isSupported(file: FileObject, root: FileObject?): Boolean = file.isZip() || file.isXedPackage()

    override val type = FileActionType(file = true, folder = false, rootFolder = false)
}

object InstallPackageAction : FileAction() {
    override val icon = Icon.ResourceIcon(drawables.download)
    override val title = strings.install.getString()

    override suspend fun action(context: FileActionContext) {
        context.viewModel.viewModelScope.launch { IntentHandleRegistry.handleIntent(context.file) }
    }

    override suspend fun isSupported(file: FileObject, root: FileObject?): Boolean = file.isXedPackage()

    override val type = FileActionType(file = true, folder = false, rootFolder = false)
}
