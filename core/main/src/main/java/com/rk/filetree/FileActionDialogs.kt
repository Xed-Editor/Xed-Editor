package com.rk.filetree

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rk.activities.main.MainActivity
import com.rk.activities.main.drawerStateRef
import com.rk.components.PropertiesDialog
import com.rk.components.SingleInputDialog
import com.rk.file.FileObject
import com.rk.file.FileOperations
import com.rk.resources.fillPlaceholders
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.utils.errorDialog
import com.rk.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun FileActionDialogs(viewModel: FileTreeViewModel, scope: CoroutineScope, context: Context) {
    if (viewModel.showRenameDialog) {
        val file = viewModel.renameFile ?: return
        SingleInputDialog(
            title = if (file.isFile()) stringResource(strings.rename_file) else stringResource(strings.rename_folder),
            inputLabel = stringResource(id = strings.new_name),
            inputValue = viewModel.renameValue,
            errorMessage = viewModel.renameError,
            confirmEnabled = viewModel.renameValue.isNotBlank() && viewModel.renameValue != file.getName(),
            confirmText = stringResource(strings.rename),
            onInputValueChange = {
                viewModel.renameValue = it
                viewModel.renameError = null

                if (it.contains(Regex("""[\p{Cntrl}/\\<>:"|?*]"""))) {
                    viewModel.renameError = context.getString(strings.invalid_characters)
                }
            },
            onConfirm = {
                val newName = viewModel.renameValue
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
                    viewModel.updateCache(parentFile)

                    tabsToRename.forEach {
                        it.tabTitle.value = newName
                        it.file = parentFile.getChildForName(newName)
                    }
                }
            },
            onFinish = { viewModel.closeRenameDialog() },
        )
    }

    if (viewModel.showDeleteConfirmation) {
        val files = viewModel.deleteFiles ?: return
        val root = viewModel.deleteRoot
        DeleteConfirmationDialog(
            files = files,
            onConfirm = {
                scope.launch {
                    for (file in files) {
                        viewModel.withFileOperation {
                            FileOperations.deleteFile(file)
                                .onFailure { toast(it.message ?: strings.delete_failed.getString()) }
                                .onSuccess {
                                    val parentFile = file.getParentFile()
                                    if (parentFile != null) {
                                        viewModel.updateCache(file.getParentFile()!!)
                                    }

                                    if (file == root) {
                                        removeProject(file, true)
                                    }

                                    MainActivity.instance?.viewModel?.also { viewModel ->
                                        viewModel.tabs.forEachIndexed { index, tab ->
                                            if (tab.file == file) {
                                                viewModel.tabManager.removeTab(index)
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
                viewModel.closeDeleteConfirmation()
            },
            onDismiss = { viewModel.closeDeleteConfirmation() },
        )
    }

    if (viewModel.showPropertiesDialog) {
        val file = viewModel.propertyFile ?: return
        PropertiesDialog(file = file, onDismiss = { viewModel.closePropertiesDialog() })
    }

    if (viewModel.showCreateDialog) {
        val file = viewModel.createParentFile ?: return
        val root = viewModel.createRoot
        SingleInputDialog(
            title =
                if (viewModel.isCreateFile) stringResource(strings.new_file) else stringResource(strings.new_folder),
            inputLabel =
                if (viewModel.isCreateFile) stringResource(id = strings.file_name)
                else stringResource(id = strings.folder_name),
            inputValue = viewModel.createValue,
            errorMessage = viewModel.createError,
            confirmEnabled = viewModel.createValue.isNotBlank(),
            confirmText = stringResource(strings.create),
            onInputValueChange = {
                viewModel.createValue = it
                viewModel.createError = null

                if (
                    viewModel.isCreateFile && it.contains(Regex("""[\p{Cntrl}/\\<>:"|?*]""")) ||
                        !viewModel.isCreateFile && it.contains(Regex("""[\p{Cntrl}\\<>:"|?*]|^/"""))
                ) {
                    viewModel.createError = context.getString(strings.invalid_characters)
                }
            },
            onConfirm = {
                scope.launch {
                    runCatching {
                            if (!file.canWrite()) {
                                toast(strings.permission_denied)
                                return@launch
                            }
                            if (!file.hasChild(viewModel.createValue)) {
                                val newChild = file.createChild(viewModel.isCreateFile, viewModel.createValue)

                                if (newChild == null) {
                                    if (viewModel.isCreateFile) {
                                        toast(strings.file_creation_failed)
                                    } else {
                                        toast(strings.folder_creation_failed)
                                    }
                                }

                                if (viewModel.isCreateFile && newChild != null && Settings.auto_open_new_files) {
                                    MainActivity.instance
                                        ?.viewModel
                                        ?.editorManager
                                        ?.openFile(
                                            newChild,
                                            projectRoot = root,
                                            checkDuplicate = true,
                                            switchToTab = true,
                                        )
                                    drawerStateRef.get()?.close()
                                }
                            } else {
                                val msg =
                                    if (viewModel.isCreateFile) strings.file_already_exists
                                    else strings.folder_already_exists
                                toast(msg.getFilledString(viewModel.createValue))
                            }

                            viewModel.updateCache(file)
                            viewModel.createValue = ""
                        }
                        .onFailure { errorDialog(it) }
                }
            },
            onFinish = { viewModel.closeCreateDialog() },
        )
    }

    if (viewModel.showCloseProjectConfirmation) {
        val root = viewModel.projectConfirmationRoot ?: return
        ProjectCloseConfirmationDialog(
            projectName = root.getAppropriateName(),
            onConfirm = {
                removeProject(root)
                viewModel.closeCloseProjectConfirmation()
            },
            onDismiss = { viewModel.closeCloseProjectConfirmation() },
        )
    }
}

@Composable
fun ProjectCloseConfirmationDialog(projectName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.close)) },
        text = { Column { Text(text = stringResource(strings.close_current_project).fillPlaceholders(projectName)) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(strings.close)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
    )
}

@Composable
fun DeleteConfirmationDialog(files: List<FileObject>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.delete)) },
        text = {
            Column {
                val text =
                    if (files.size == 1) {
                        stringResource(strings.ask_deletion_one).fillPlaceholders(files.first().getName())
                    } else {
                        stringResource(strings.ask_deletion_many).fillPlaceholders(files.size)
                    }
                Text(text)
            }
        },
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
