package com.rk.filetree

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.rk.activities.main.MainActivity
import com.rk.activities.main.filterWithFiles
import com.rk.activities.main.ui.drawerStateRef
import com.rk.components.PropertiesDialog
import com.rk.components.SingleInputDialog
import com.rk.drawer.DrawerViewModel
import com.rk.events.Events
import com.rk.events.FileEvent
import com.rk.file.FileObject
import com.rk.file.FileOperations
import com.rk.file.FileValidation
import com.rk.resources.fillPlaceholders
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.errorDialog
import com.rk.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun FileActionDialogs(
    drawerViewModel: DrawerViewModel,
    viewModel: FileTreeViewModel,
    scope: CoroutineScope,
    context: Context,
) {
    val showRenameDialog by viewModel.showRenameDialog.collectAsStateWithLifecycle()
    val renameFile by viewModel.renameFile.collectAsStateWithLifecycle()
    val renameValue by viewModel.renameValue.collectAsStateWithLifecycle()
    val renameError by viewModel.renameError.collectAsStateWithLifecycle()
    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsStateWithLifecycle()
    val deleteFiles by viewModel.deleteFiles.collectAsStateWithLifecycle()
    val deleteRoot by viewModel.deleteRoot.collectAsStateWithLifecycle()
    val showPropertiesDialog by viewModel.showPropertiesDialog.collectAsStateWithLifecycle()
    val propertyFile by viewModel.propertyFile.collectAsStateWithLifecycle()
    val showCreateDialog by viewModel.showCreateDialog.collectAsStateWithLifecycle()
    val createParentFile by viewModel.createParentFile.collectAsStateWithLifecycle()
    val createRoot by viewModel.createRoot.collectAsStateWithLifecycle()
    val isCreateFile by viewModel.isCreateFile.collectAsStateWithLifecycle()
    val createValue by viewModel.createValue.collectAsStateWithLifecycle()
    val createError by viewModel.createError.collectAsStateWithLifecycle()
    val showCloseProjectConfirmation by viewModel.showCloseProjectConfirmation.collectAsStateWithLifecycle()
    val projectConfirmationRoot by viewModel.projectConfirmationRoot.collectAsStateWithLifecycle()

    if (showRenameDialog) {
        val file = renameFile ?: return
        SingleInputDialog(
            title = if (file.isFile()) stringResource(strings.rename_file) else stringResource(strings.rename_folder),
            inputLabel = stringResource(id = strings.new_name),
            inputValue = renameValue,
            errorMessage = renameError,
            confirmEnabled = renameValue.isNotBlank() && renameValue != file.getName(),
            confirmText = stringResource(strings.rename),
            onInputValueChange = {
                viewModel.setRenameValue(it)
                viewModel.setRenameError(null)

                if (it.contains(FileValidation.INVALID_NAME_CHARS)) {
                    viewModel.setRenameError(context.getString(strings.invalid_characters))
                }
            },
            onConfirm = {
                val newName = renameValue
                scope.launch {
                    val oldPath = file.getAbsolutePath()
                    val mainViewModel = MainActivity.instance?.viewModel
                    val tabsToRename =
                        mainViewModel?.editorTabs?.filterWithFiles { _, file ->
                            file.getAbsolutePath() == oldPath
                        } ?: emptyList()

                    val success = file.renameTo(newName)
                    if (!success) {
                        toast(strings.rename_failed)
                        return@launch
                    }

                    Events.publish(FileEvent.Renamed(file, oldPath))

                    val parentFile = file.getParentFile() ?: return@launch
                    viewModel.updateCache(parentFile)

                    tabsToRename.forEach {
                        it.title = newName
                        it.file = parentFile.getChild(newName)
                    }
                }
            },
            onFinish = { viewModel.closeRenameDialog() },
        )
    }

    if (showDeleteConfirmation) {
        val files = deleteFiles ?: return
        val root = deleteRoot
        DeleteConfirmationDialog(
            files = files,
            onConfirm = {
                scope.launch {
                    for (file in files) {
                        val path = file.getAbsolutePath()
                        viewModel.withFileOperation {
                            FileOperations.deleteFile(file)
                                .onFailure {
                                    toast(it.message ?: strings.delete_failed.getString())
                                    val parentFile = file.getParentFile()
                                    if (parentFile != null) {
                                        viewModel.updateCache(file.getParentFile()!!)
                                    }else{
                                        viewModel.updateCache(file)
                                    }
                                }
                                .onSuccess {
                                    Events.publish(FileEvent.Deleted(path))
                                    val parentFile = file.getParentFile()
                                    if (parentFile != null) {
                                        viewModel.updateCache(file.getParentFile()!!)
                                    }else{
                                        viewModel.updateCache(file)
                                    }

                                    if (file == root) {
                                        drawerViewModel.removeFileTreeTab(file, true)
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

    if (showPropertiesDialog) {
        val file = propertyFile ?: return
        PropertiesDialog(file = file, onDismiss = { viewModel.closePropertiesDialog() })
    }

    if (showCreateDialog) {
        val file = createParentFile ?: return
        val root = createRoot
        SingleInputDialog(
            title =
                if (isCreateFile) stringResource(strings.new_file) else stringResource(strings.new_folder),
            inputLabel =
                if (isCreateFile) stringResource(id = strings.file_name)
                else stringResource(id = strings.folder_name),
            inputValue = createValue,
            errorMessage = createError,
            confirmEnabled = createValue.isNotBlank(),
            confirmText = stringResource(strings.create),
            onInputValueChange = {
                viewModel.setCreateValue(it)
                viewModel.setCreateError(null)

                if (
                    isCreateFile && it.contains(FileValidation.INVALID_NAME_CHARS) ||
                        !isCreateFile && it.contains(FileValidation.INVALID_FOLDER_PATH_CHARS)
                ) {
                    viewModel.setCreateError(context.getString(strings.invalid_characters))
                }
            },
            onConfirm = {
                scope.launch {
                    runCatching {
                        if (!file.canWrite()) {
                            toast(strings.permission_denied)
                            return@launch
                        }
                        if (!file.hasChild(createValue)) {
                            val newChild = file.createChild(isCreateFile, createValue)

                            if (newChild == null) {
                                if (isCreateFile) {
                                    toast(strings.file_creation_failed)
                                } else {
                                    toast(strings.folder_creation_failed)
                                }
                            } else {
                                Events.publish(FileEvent.Created(newChild))
                            }

                            if (isCreateFile && newChild != null && Settings.auto_open_new_files) {
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
                                if (isCreateFile) strings.file_already_exists
                                else strings.folder_already_exists
                            toast(msg.getFilledString(createValue))
                        }

                        viewModel.updateCache(file)
                        viewModel.setCreateValue("")
                    }
                        .onFailure { errorDialog(throwable = it) }
                }
            },
            onFinish = { viewModel.closeCreateDialog() },
        )
    }

    if (showCloseProjectConfirmation) {
        val root = projectConfirmationRoot ?: return
        ProjectCloseConfirmationDialog(
            projectName = root.getAppropriateName(),
            onConfirm = {
                drawerViewModel.removeFileTreeTab(root)
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
