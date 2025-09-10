package com.rk.xededitor.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.DividerColumn
import com.rk.compose.filetree.fileTreeViewModel
import com.rk.compose.filetree.removeProject
import com.rk.extension.Hooks
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.UriWrapper
import com.rk.file.openWith
import com.rk.file.to_save_file
import com.rk.libcommons.errorDialog
import com.rk.libcommons.showTerminalNotice
import com.rk.libcommons.toast
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.tabs.EditorTab
import com.rk.xededitor.ui.activities.main.MainActivity
import com.rk.xededitor.ui.activities.terminal.Terminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.io.Util.copyStream
import java.util.Locale

@Composable
fun FileActionDialog(
    modifier: Modifier = Modifier,
    file: FileObject,
    root: FileObject?,
    onDismissRequest: () -> Unit,
    fileTreeContext: Boolean = true
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val scope = rememberCoroutineScope()

    // State for various dialogs
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showNewDialog by remember { mutableStateOf(false) }
    var showXedDialog by remember { mutableStateOf(true) }

    if (showXedDialog){
        XedDialog(onDismissRequest = onDismissRequest) {
            DividerColumn(modifier = Modifier
                .padding(0.dp)
                .verticalScroll(rememberScrollState())) {

                if (fileTreeContext && root != null){
                    AddDialogItem(
                        icon = Icons.Outlined.Close,
                        title = stringResource(strings.close),
                        //description = stringResource(strings.close_current_project),
                        onClick = {
                            removeProject(root, true)
                            showXedDialog = true
                            onDismissRequest()
                        }
                    )
                }


                if (file.isDirectory()) {
                    AddDialogItem(
                        icon = Icons.Outlined.Refresh,
                        title = stringResource(strings.refresh),
                        //description = stringResource(strings.reload_file_tree),
                        onClick = {
                            fileTreeViewModel?.updateCache(file)
                            showXedDialog = true
                            onDismissRequest()
                        }
                    )
                    
                }

                if (file is FileWrapper && file.isDirectory()){
                    AddDialogItem(
                        icon = drawables.terminal,
                        title = stringResource(strings.open_in_terminal),
                        //description = stringResource(strings.open_in_terminal),
                        onClick = {
                            showTerminalNotice(activity = MainActivity.instance!!){
                                val intent = Intent(context,Terminal::class.java)
                                intent.putExtra("cwd",file.getAbsolutePath())
                                context.startActivity(intent)
                                onDismissRequest()
                            }
                        }
                    )
                    
                }

                if (file.isDirectory()){
                    AddDialogItem(
                        icon = Icons.Outlined.Add,
                        title = stringResource(strings.new_document),
                        //description = stringResource(strings.new_document_desc),
                        onClick = {
                            showXedDialog = false
                           showNewDialog = true
                        }
                    )

                }

                AddDialogItem(
                    icon = Icons.Outlined.Edit,
                    title = stringResource(strings.rename),
                    //description = stringResource(strings.rename_descript),
                    onClick = {
                        showXedDialog = false
                        showRenameDialog = true
                    }
                )
                

                AddDialogItem(
                    icon = Icons.Outlined.Delete,
                    title = stringResource(strings.delete),
                    //description = stringResource(strings.delete_descript),
                    onClick = {
                        showXedDialog = false
                        showDeleteDialog = true
                    }
                )
                

                AddDialogItem(
                    icon = if (file.isFile()) drawables.content_copy_24px else drawables.round_content_paste_20,
                    title = stringResource(strings.copy),
                    //description = stringResource(strings.copy_desc),
                    onClick = {
                        scope.launch {
                            FileOperations.copyToClipboard(file)
                            toast(context.getString(strings.copied))
                            showXedDialog = true
                            onDismissRequest()
                        }
                    }
                )
                

                AddDialogItem(
                    icon = drawables.round_content_cut_20,
                    title = stringResource(strings.cut),
                    //description = stringResource(strings.cut_desc),
                    onClick = {
                        scope.launch {
                            FileOperations.copyToClipboard(file,isCut = true)
                            showXedDialog = true
                            onDismissRequest()
                        }
                    }
                )
                

                if (fileTreeContext && FileOperations.clipboard != null && file.isDirectory()){
                    AddDialogItem(
                        icon = drawables.round_content_paste_20,
                        title = stringResource(strings.paste),
                        //description = stringResource(strings.paste_desc),
                        onClick = {
                            scope.launch {
                                val parentFile = FileOperations.clipboard!!.getParentFile()
                                pasteFile(context,FileOperations.clipboard!!,file,isCut = FileOperations.isCut)
                                fileTreeViewModel?.updateCache(file)
                                fileTreeViewModel?.updateCache(parentFile!!)
                                //showXedDialog = true
                                onDismissRequest()
                            }
                        }
                    )
                    
                }


                AddDialogItem(
                    icon = Icons.AutoMirrored.Outlined.ExitToApp,
                    title = stringResource(strings.open_with),
                    //description = stringResource(strings.open_with_other),
                    onClick = {
                        FileOperations.openWithExternalApp(context, file)
                        //showXedDialog = true
                        onDismissRequest()
                    }
                )
                

                AddDialogItem(
                    icon = drawables.file_symlink,
                    title = stringResource(strings.save_as),
                    //description = stringResource(strings.save_desc),
                    onClick = {
                        // This would typically open a file picker
                        FileOperations.saveAs(context, file)
                        //showXedDialog = true
                        onDismissRequest()
                    }
                )
                

                if (fileTreeContext && file.isDirectory()){
                    AddDialogItem(
                        icon = drawables.arrow_downward,
                        title = stringResource(strings.add_file),
                        //description = stringResource(strings.add_file_desc),
                        onClick = {
                            // This would typically open a file picker
                            FileOperations.addFile(file)
                            //showXedDialog = true
                            onDismissRequest()
                        }
                    )
                    
                }

                AddDialogItem(
                    icon = Icons.Outlined.Info,
                    title = stringResource(strings.info),
                    //description = stringResource(strings.file_info),
                    onClick = {
                        showXedDialog = false
                        showInfoDialog = true
                        //onDismissRequest()
                    }
                )

                Hooks.FileAction.actions.values.forEach { action ->
                    if (action.shouldAttach(root,file)){
                        AddDialogItem(
                            icon = action.icon,
                            title = action.title,
                            onClick = {
                                action.onClick(root,file)
                            }
                        )
                    }

                }
                
            }
        }
    }


    // Rename Dialog
    if (showRenameDialog) {
        RenameDialog(
            currentName = file.getName(),
            onConfirm = { newName ->
                scope.launch {
                    val parentFile = file.getParentFile()
                    val success = FileOperations.renameFile(file, newName)
                    if (success) {
                        if (parentFile != null){
                            fileTreeViewModel?.updateCache(file.getParentFile()!!)
                            MainActivity.instance?.apply {
                                val targetTab = viewModel.tabs.
                                find { it is EditorTab && it.file == file } as? EditorTab

                                targetTab?.tabTitle?.value = newName
                                targetTab?.file = parentFile.getChildForName(newName)
                            }
                        }
                    }
                }
                showRenameDialog = false
                //showXedDialog = true
                onDismissRequest()
            },
            onDismiss = {
                showXedDialog = true
                showRenameDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            fileName = file.getName(),
            onConfirm = {
                scope.launch {
                    val success = FileOperations.deleteFile(file)
                    if (success) {
                        fileTreeViewModel?.updateCache(file.getParentFile()!!)
                        toast(context.getString(strings.success))
                    }
                }
                showDeleteDialog = false
                //showXedDialog = true
                onDismissRequest()
            },
            onDismiss = {
                showXedDialog = true
                showDeleteDialog = false
            }
        )
    }

    // Info Dialog
    if (showInfoDialog) {
        FileInfoDialog(
            file = file,
            onDismiss = {
                showXedDialog = true
                showInfoDialog = false
                onDismissRequest()
            }
        )
    }

    if (showNewDialog){
        NewDocumentDialog(
            parentFile = file, onDismiss = {
                showXedDialog = true
                showNewDialog = false
            },
            onConfirm = {
                fileTreeViewModel?.updateCache(file)
                showXedDialog = true
                showNewDialog = false
                onDismissRequest()
            }
        )
    }
}

// File Operations utility class
object FileOperations {
    var clipboard: FileObject? = null
    var isCut: Boolean = false

    suspend fun copyToClipboard(file: FileObject,isCut: Boolean = false) {
        clipboard = file
        this.isCut = isCut
    }

    suspend fun renameFile(file: FileObject, newName: String): Boolean {
        return try {
            file.renameTo(newName)
        } catch (e: Exception) {
            errorDialog(e)
            false
        }
    }

    suspend fun deleteFile(file: FileObject): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            errorDialog(e)
            false
        }
    }

    fun openWithExternalApp(context: Context, file: FileObject) {
        openWith(context,file)
    }

    fun saveAs(context: Context, file: FileObject) {
        to_save_file = file
        MainActivity.instance?.fileManager?.requestOpenDirectoryToSaveFile(file)
    }

    fun addFile(parentFile: FileObject){
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        MainActivity.instance?.fileManager?.requestAddFile(parentFile)
    }
}

// Rename Dialog Component
@Composable
fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    XedDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(strings.rename_file),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(stringResource(strings.new_name)) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(strings.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = { onConfirm(newName) },
                    enabled = newName.isNotBlank() && newName != currentName
                ) {
                    Text(stringResource((strings.rename)))
                }
            }
        }
    }
}

@Composable
fun NewDocumentDialog(
    parentFile: FileObject,
    onConfirm: (FileObject?) -> Unit,
    onDismiss: () -> Unit
) {

    var value by remember { mutableStateOf("") }
    XedDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(strings.new_document),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(strings.newFile_hint)) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    if (!parentFile.hasChild(value)){
                        onConfirm(parentFile.createChild(createFile = false,value))
                    }

                }) {
                    Text(stringResource(strings.folder))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (!parentFile.hasChild(value)){
                            onConfirm(parentFile.createChild(createFile = true,value))
                        }
                    },
                    enabled = value.isNotBlank()
                ) {
                    Text(stringResource(strings.file))
                }
            }
        }
    }
}

// Delete Confirmation Dialog
@Composable
fun DeleteConfirmationDialog(
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    XedDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(strings.delete),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = String.format(stringResource(strings.ask_del), fileName),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(strings.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onConfirm,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(strings.delete))
                }
            }
        }
    }
}

// File Info Dialog
@Composable
fun FileInfoDialog(
    file: FileObject,
    onDismiss: () -> Unit
) {
    XedDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(strings.file_info),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            InfoRow(stringResource(strings.name), file.getName())
            if (file.isFile()){
                InfoRow(stringResource(strings.file_size), formatFileSize(file.length()))
            }
            InfoRow(stringResource(strings.type), if (file.isDirectory()) stringResource(strings.folder) else stringResource(strings.file))
            InfoRow(stringResource(strings.can_read), if (file.canRead()) stringResource(strings.yes) else stringResource(strings.no))
            InfoRow(stringResource(strings.can_write), if (file.canWrite()) stringResource(strings.yes) else stringResource(strings.no))

            InfoRow(stringResource(strings.file_type), file.javaClass.simpleName)
            InfoRow(stringResource(strings.path), file.getAbsolutePath())

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(strings.close))
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    SelectionContainer {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "$label:",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                modifier = Modifier.weight(2f)
            )
        }
    }

}

// Utility functions
private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(),"%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(),"%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(),"%.1f GB", gb)
}



/**
 * Pastes a file or folder to the destination directory
 * @param context Android context for content resolver operations
 * @param sourceFile The file or folder to copy/move
 * @param destinationFolder The target directory
 * @param isCut Whether to move (cut) or copy the file
 * @param onProgress Optional callback for progress updates (current file being processed)
 * @return Result indicating success or failure with error message
 */
suspend fun pasteFile(
    context: Context,
    sourceFile: FileObject,
    destinationFolder: FileObject,
    isCut: Boolean = false,
    onProgress: ((String) -> Unit)? = null
): Result<Unit> = withContext(Dispatchers.IO) {

    runCatching {
        // Validation checks
        if (!destinationFolder.isDirectory()) {
            throw IllegalArgumentException("Destination must be a directory")
        }

        if (!destinationFolder.canWrite()) {
            throw IllegalStateException("Destination directory is not writable")
        }

        if (!sourceFile.exists()) {
            throw IllegalArgumentException("Source file does not exist")
        }

        // Prevent copying a directory into itself
        if (sourceFile.isDirectory() && isParentOf(sourceFile, destinationFolder)) {
            throw IllegalArgumentException("Cannot copy a directory into itself")
        }

        // Check if target already exists
        val targetName = sourceFile.getName()
        if (destinationFolder.hasChild(targetName)) {
            throw IllegalStateException("A file or folder with name '$targetName' already exists in destination")
        }

        // Perform the copy operation
        copyRecursive(context, sourceFile, destinationFolder, onProgress)

        // If it's a cut operation, delete the source
        if (isCut) {
            val deleteSuccess = sourceFile.delete()
            if (!deleteSuccess) {
                throw IllegalStateException("Failed to delete source file after moving")
            }
        }
    }
}

/**
 * Recursively copies a file or directory
 */
private suspend fun copyRecursive(
    context: Context,
    sourceFile: FileObject,
    targetParent: FileObject,
    onProgress: ((String) -> Unit)?
) {
    onProgress?.invoke("Processing: ${sourceFile.getName()}")

    if (sourceFile.isDirectory()) {
        // Create target directory
        val targetDir = targetParent.createChild(false, sourceFile.getName())
            ?: throw IllegalStateException("Failed to create directory: ${sourceFile.getName()}")

        // Copy all children
        sourceFile.listFiles().forEach { child ->
            copyRecursive(context, child, targetDir, onProgress)
        }
    } else {
        // Copy file content
        val targetFile = targetParent.createChild(true, sourceFile.getName())
            ?: throw IllegalStateException("Failed to create file: ${sourceFile.getName()}")

        context.contentResolver.openInputStream(sourceFile.toUri())?.use { inputStream ->
            context.contentResolver.openOutputStream(targetFile.toUri())?.use { outputStream ->
                copyStream(inputStream, outputStream)
            } ?: throw IllegalStateException("Failed to open output stream for: ${sourceFile.getName()}")
        } ?: throw IllegalStateException("Failed to open input stream for: ${sourceFile.getName()}")
    }
}

/**
 * Checks if parentDir is a parent of childDir (prevents copying directory into itself)
 */
private fun isParentOf(parentDir: FileObject, childDir: FileObject): Boolean {
    var current: FileObject? = childDir
    while (current != null) {
        if (current == parentDir) {
            return true
        }
        current = current.getParentFile()
    }
    return false
}

/**
 * Convenience function for copying files
 */
suspend fun copyFile(
    context: Context,
    sourceFile: FileObject,
    destinationFolder: FileObject,
    onProgress: ((String) -> Unit)? = null
): Result<Unit> = pasteFile(context, sourceFile, destinationFolder, isCut = false, onProgress)

/**
 * Convenience function for moving files
 */
suspend fun moveFile(
    context: Context,
    sourceFile: FileObject,
    destinationFolder: FileObject,
    onProgress: ((String) -> Unit)? = null
): Result<Unit> = pasteFile(context, sourceFile, destinationFolder, isCut = true, onProgress)