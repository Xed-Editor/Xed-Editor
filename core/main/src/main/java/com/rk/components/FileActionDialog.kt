package com.rk.components

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.drawerStateRef
import com.rk.activities.terminal.Terminal
import com.rk.file.FileObject
import com.rk.file.FileOperations
import com.rk.file.FileWrapper
import com.rk.filetree.FileTreeTab
import com.rk.filetree.FileTreeViewModel
import com.rk.filetree.addProject
import com.rk.filetree.drawerTabs
import com.rk.filetree.getAppropriateName
import com.rk.filetree.removeProject
import com.rk.icons.CreateNewFile
import com.rk.icons.CreateNewFolder
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.fillPlaceholders
import com.rk.resources.getFilledString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.app.InbuiltFeatures
import com.rk.tabs.editor.EditorTab
import com.rk.utils.errorDialog
import com.rk.utils.showTerminalNotice
import com.rk.utils.toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionDialog(
    file: FileObject,
    root: FileObject?,
    onDismissRequest: () -> Unit,
    fileTreeContext: Boolean = true,
    fileTreeViewModel: FileTreeViewModel,
) {
    val context = LocalContext.current
    val scope = DefaultScope

    // State for various dialogs
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(file.getName()) }
    var renameError by remember { mutableStateOf<String?>(null) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showXedDialog by remember { mutableStateOf(true) }

    var isNewFile by remember { mutableStateOf(true) }
    var newNameValue by remember { mutableStateOf("") }
    var newNameError by remember { mutableStateOf<String?>(null) }
    var showNewDialog by remember { mutableStateOf(false) }
    var showCloseProjectDialog by remember { mutableStateOf(false) }

    if (showXedDialog) {
        ModalBottomSheet(onDismissRequest = onDismissRequest) {
            Column(
                modifier =
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)
                        .verticalScroll(rememberScrollState())
            ) {
                if (file == root) {
                    AddDialogItem(
                        icon = Icons.Outlined.Close,
                        title = stringResource(strings.close),
                        onClick = {
                            showCloseProjectDialog = true
                            showXedDialog = true
                        },
                    )
                }

                if (file.isDirectory()) {
                    AddDialogItem(
                        icon = Icons.Outlined.Refresh,
                        title = stringResource(strings.refresh),
                        // description = stringResource(strings.reload_file_tree),
                        onClick = {
                            fileTreeViewModel.updateCache(file)
                            showXedDialog = true
                            onDismissRequest()
                        },
                    )
                }

                if (InbuiltFeatures.terminal.state.value && file is FileWrapper && file.isDirectory()) {
                    AddDialogItem(
                        icon = drawables.terminal,
                        title = stringResource(strings.open_in_terminal),
                        // description = stringResource(strings.open_in_terminal),
                        onClick = {
                            showTerminalNotice(activity = MainActivity.instance!!) {
                                val intent = Intent(context, Terminal::class.java)
                                intent.putExtra("cwd", file.getAbsolutePath())
                                context.startActivity(intent)
                                onDismissRequest()
                            }
                        },
                    )
                }

                if (file.isDirectory()) {
                    AddDialogItem(
                        icon = XedIcons.CreateNewFile,
                        title = stringResource(strings.new_file),
                        // description = stringResource(strings.new_document_desc),
                        onClick = {
                            isNewFile = true
                            showXedDialog = false
                            showNewDialog = true
                        },
                    )

                    AddDialogItem(
                        icon = XedIcons.CreateNewFolder,
                        title = stringResource(strings.new_folder),
                        // description = stringResource(strings.new_document_desc),
                        onClick = {
                            isNewFile = false
                            showXedDialog = false
                            showNewDialog = true
                        },
                    )
                }

                AddDialogItem(
                    icon = Icons.Outlined.Edit,
                    title = stringResource(strings.rename),
                    // description = stringResource(strings.rename_desc),
                    onClick = {
                        showXedDialog = false
                        showRenameDialog = true
                    },
                )

                AddDialogItem(
                    icon = Icons.Outlined.Delete,
                    title = stringResource(strings.delete),
                    // description = stringResource(strings.delete_desc),
                    onClick = {
                        showXedDialog = false
                        showDeleteDialog = true
                    },
                )

                AddDialogItem(
                    icon = drawables.copy,
                    title = stringResource(strings.copy),
                    // description = stringResource(strings.copy_desc),
                    onClick = {
                        scope.launch {
                            FileOperations.copyToClipboard(file)
                            toast(context.getString(strings.copied))
                            showXedDialog = true
                            onDismissRequest()
                        }
                    },
                )

                AddDialogItem(
                    icon = drawables.cut,
                    title = stringResource(strings.cut),
                    // description = stringResource(strings.cut_desc),
                    onClick = {
                        scope.launch {
                            FileOperations.copyToClipboard(file, isCut = true)
                            fileTreeViewModel.markNodeAsCut(file)
                            showXedDialog = true
                            onDismissRequest()
                        }
                    },
                )

                if (fileTreeContext && FileOperations.clipboard != null && file.isDirectory()) {
                    AddDialogItem(
                        icon = drawables.paste,
                        title = stringResource(strings.paste),
                        // description = stringResource(strings.paste_desc),
                        onClick = {
                            scope.launch {
                                val isCut = FileOperations.isCut
                                val clipboardFile = FileOperations.clipboard!!
                                val clipboardParentFile = clipboardFile.getParentFile()

                                fileTreeViewModel.withFileOperation {
                                    FileOperations.pasteFile(
                                        context = context,
                                        sourceFile = clipboardFile,
                                        destinationFolder = file,
                                        isCut = isCut,
                                    )
                                }

                                if (FileOperations.isCut) {
                                    MainActivity.instance?.apply {
                                        val targetTab =
                                            viewModel.tabs.find { it is EditorTab && it.file == clipboardFile }
                                                as? EditorTab
                                        targetTab?.file = file.getChildForName(clipboardFile.getName())
                                    }
                                }

                                fileTreeViewModel.updateCache(file)
                                fileTreeViewModel.updateCache(clipboardParentFile!!)
                                fileTreeViewModel.unmarkNodeAsCut(clipboardFile)

                                // showXedDialog = true
                                onDismissRequest()
                            }
                        },
                    )
                }

                AddDialogItem(
                    icon = Icons.AutoMirrored.Outlined.ExitToApp,
                    title = stringResource(strings.open_with),
                    // description = stringResource(strings.open_with_other),
                    onClick = {
                        scope.launch { FileOperations.openWithExternalApp(context, file) }

                        // showXedDialog = true
                        onDismissRequest()
                    },
                )

                AddDialogItem(
                    icon = drawables.file_symlink,
                    title = stringResource(strings.save_as),
                    // description = stringResource(strings.save_desc),
                    onClick = {
                        // This would typically open a file picker
                        FileOperations.saveAs(file)
                        // showXedDialog = true
                        onDismissRequest()
                    },
                )

                if (fileTreeContext && file.isDirectory()) {
                    AddDialogItem(
                        icon = drawables.arrow_downward,
                        title = stringResource(strings.add_file),
                        // description = stringResource(strings.add_file_desc),
                        onClick = {
                            // This would typically open a file picker
                            FileOperations.addFile(file)
                            // showXedDialog = true
                            onDismissRequest()
                        },
                    )
                }

                if (fileTreeContext && file.isDirectory() && !drawerTabs.any { it is FileTreeTab && it.root == file }) {
                    AddDialogItem(
                        icon = drawables.folder_code,
                        title = stringResource(strings.open_as_project),
                        // description = stringResource(strings.add_file_desc),
                        onClick = {
                            // This would typically open a file picker
                            addProject(file, true)
                            // showXedDialog = true
                            onDismissRequest()
                        },
                    )
                }

                AddDialogItem(
                    icon = Icons.Outlined.Info,
                    title = stringResource(strings.properties),
                    // description = stringResource(strings.file_info),
                    onClick = {
                        showXedDialog = false
                        showInfoDialog = true
                        // onDismissRequest()
                    },
                )
            }
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        SingleInputDialog(
            title = if (file.isFile()) stringResource(strings.rename_file) else stringResource(strings.rename_folder),
            inputLabel = stringResource(id = strings.new_name),
            inputValue = renameValue,
            errorMessage = renameError,
            confirmEnabled = renameValue.isNotBlank() && renameValue != file.getName(),
            confirmText = stringResource(strings.rename),
            onInputValueChange = {
                renameValue = it
                renameError = null

                if (renameValue.contains(Regex("""[\p{Cntrl}/\\<>:"|?*]"""))) {
                    renameError = context.getString(strings.invalid_characters)
                }
            },
            onConfirm = {
                val newName = renameValue
                scope.launch {
                    val oldPath = file.getAbsolutePath()
                    val tabsToRename =
                        MainActivity.instance?.viewModel?.tabs?.filterIsInstance<EditorTab>()?.filter {
                            it.file.getAbsolutePath() == oldPath
                        } ?: emptyList()

                    val success = file.renameTo(newName)
                    if (!success) {
                        toast(strings.rename_failed)
                        return@launch
                    }

                    val parentFile = file.getParentFile() ?: return@launch
                    fileTreeViewModel.updateCache(parentFile)

                    tabsToRename.forEach {
                        it.tabTitle.value = newName
                        it.file = parentFile.getChildForName(newName)
                    }
                }

                onDismissRequest()
            },
            onFinish = {
                renameValue = file.getName()
                renameError = null
                showXedDialog = true
                showRenameDialog = false
            },
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            fileName = file.getName(),
            onConfirm = {
                scope.launch {
                    var success = false
                    fileTreeViewModel.withFileOperation { success = FileOperations.deleteFile(file) }

                    if (!success) {
                        toast(strings.delete_failed)
                        return@launch
                    }

                    val parentFile = file.getParentFile()
                    if (parentFile != null) {
                        fileTreeViewModel.updateCache(file.getParentFile()!!)
                    }

                    if (file == root) {
                        removeProject(file, true)
                    }

                    MainActivity.instance?.viewModel?.also { viewModel ->
                        viewModel.tabs.forEachIndexed { index, tab ->
                            if (tab.file == file) {
                                viewModel.removeTab(index)
                            }
                        }
                    }
                }
                showDeleteDialog = false
                // showXedDialog = true
                onDismissRequest()
            },
            onDismiss = {
                showXedDialog = true
                showDeleteDialog = false
            },
        )
    }

    // Info dialog
    if (showInfoDialog) {
        PropertiesDialog(
            file = file,
            onDismiss = {
                showXedDialog = true
                showInfoDialog = false
                onDismissRequest()
            },
        )
    }

    // New file/folder dialog
    if (showNewDialog) {
        SingleInputDialog(
            title = if (isNewFile) stringResource(strings.new_file) else stringResource(strings.new_folder),
            inputLabel =
                if (isNewFile) stringResource(id = strings.file_name) else stringResource(id = strings.folder_name),
            inputValue = newNameValue,
            errorMessage = newNameError,
            confirmEnabled = newNameValue.isNotBlank(),
            confirmText = stringResource(strings.create),
            onInputValueChange = {
                newNameValue = it
                newNameError = null

                if (
                    isNewFile && newNameValue.contains(Regex("""[\p{Cntrl}/\\<>:"|?*]""")) ||
                        !isNewFile && newNameValue.contains(Regex("""[\p{Cntrl}\\<>:"|?*]|^/"""))
                ) {
                    newNameError = context.getString(strings.invalid_characters)
                }
            },
            onConfirm = {
                DefaultScope.launch {
                    runCatching {
                            if (file.canWrite().not()) {
                                toast(strings.permission_denied)
                                return@launch
                            }
                            if (file.hasChild(newNameValue).not()) {
                                val newChild = file.createChild(createFile = isNewFile, newNameValue)

                                if (newChild == null) {
                                    val msg =
                                        if (isNewFile) strings.file_creation_failed else strings.folder_creation_failed
                                    toast(msg)
                                }

                                if (isNewFile && newChild != null && Settings.auto_open_new_files) {
                                    MainActivity.instance
                                        ?.viewModel
                                        ?.newTab(newChild, checkDuplicate = true, switchToTab = true)
                                    drawerStateRef.get()?.close()
                                }
                            } else {
                                val msg = if (isNewFile) strings.file_already_exists else strings.folder_already_exists
                                toast(msg.getFilledString(newNameValue))
                            }

                            fileTreeViewModel.updateCache(file)
                            newNameValue = ""
                        }
                        .onFailure { errorDialog(it) }
                }

                onDismissRequest()
            },
            onFinish = {
                newNameError = null
                showXedDialog = true
                showNewDialog = false
            },
        )
    }

    if (showCloseProjectDialog && root != null) {
        CloseConfirmationDialog(
            projectName = root.getAppropriateName(),
            onConfirm = {
                removeProject(root)
                showCloseProjectDialog = false
                onDismissRequest()
            },
            onDismiss = {
                showCloseProjectDialog = false
                onDismissRequest()
            },
        )
    }
}

@Composable
fun CloseConfirmationDialog(projectName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.close)) },
        text = { Column { Text(text = stringResource(strings.close_current_project).fillPlaceholders(projectName)) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(strings.close)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
    )
}

// Delete Confirmation Dialog
@Composable
fun DeleteConfirmationDialog(fileName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.delete)) },
        text = { Column { Text(text = stringResource(strings.ask_del).fillPlaceholders(fileName)) } },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(strings.delete))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
    )
}
