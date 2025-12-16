package com.rk.components

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.fileTreeViewModel
import com.rk.activities.main.navigationDrawerState
import com.rk.activities.settings.SettingsActivity
import com.rk.commands.CommandProvider
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.toFileObject
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentTab
import com.rk.icons.CreateNewFile
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.app.InbuiltFeatures
import com.rk.utils.application
import com.rk.utils.errorDialog
import com.rk.utils.getTempDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

var addDialog by mutableStateOf(false)
var fileSearchDialog by mutableStateOf(false)
var codeSearchDialog by mutableStateOf(false)
var projectSearchReplaceDialog by mutableStateOf(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowScope.GlobalActions(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tempFileNameDialog by remember { mutableStateOf(false) }

    if (viewModel.tabs.isEmpty() || viewModel.currentTab?.showGlobalActions == true) {
        IconButton(onClick = { addDialog = true }) { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) }

        if (InbuiltFeatures.terminal.state.value) {
            val terminalAction = CommandProvider.getForId("global.terminal")

            IconButton(onClick = { terminalAction!!.action(viewModel, context as Activity) }) {
                Icon(painter = painterResource(drawables.terminal), contentDescription = null)
            }
        }

        IconButton(
            onClick = {
                val intent = Intent(context, SettingsActivity::class.java)
                context.startActivity(intent)
            }
        ) {
            Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
        }
    }

    if (fileSearchDialog && currentTab is FileTreeTab) {
        FileSearchDialog(
            projectFile = (currentTab as FileTreeTab).root,
            onFinish = { fileSearchDialog = false },
            onSelect = { projectFile, fileObject ->
                scope.launch {
                    if (fileObject.isFile()) {
                        viewModel.newTab(fileObject = fileObject, checkDuplicate = true, switchToTab = true)
                    } else {
                        fileTreeViewModel.get()?.goToFolder(projectFile, fileObject)
                        navigationDrawerState.get()!!.open()
                    }
                }
            },
        )
    }

    if (codeSearchDialog && currentTab is FileTreeTab) {
        CodeSearchDialog(
            viewModel = viewModel,
            projectFile = (currentTab as FileTreeTab).root,
            onFinish = { codeSearchDialog = false },
        )
    }

    if (projectSearchReplaceDialog && currentTab is FileTreeTab) {
        ProjectSearchReplaceDialog(
            viewModel = viewModel,
            projectFile = (currentTab as FileTreeTab).root,
            onFinish = { projectSearchReplaceDialog = false },
        )
    }

    if (addDialog) {
        ModalBottomSheet(onDismissRequest = { addDialog = false }) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)) {
                AddDialogItem(icon = drawables.file, title = stringResource(strings.temp_file)) {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.setType("application/octet-stream")
                    intent.putExtra(Intent.EXTRA_TITLE, "newfile.txt")

                    val activities =
                        application!!.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)

                    if (activities.isEmpty()) {
                        errorDialog(strings.unsupported_feature)
                    } else {
                        tempFileNameDialog = true
                    }

                    addDialog = false
                }

                AddDialogItem(icon = XedIcons.CreateNewFile, title = stringResource(strings.new_file)) {
                    addDialog = false
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.setType("application/octet-stream")
                    intent.putExtra(Intent.EXTRA_TITLE, "newfile.txt")

                    val activities =
                        application!!.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                    if (activities.isEmpty()) {
                        errorDialog(strings.unsupported_feature)
                    } else {
                        MainActivity.instance?.apply {
                            fileManager.createNewFile(mimeType = "*/*", title = "newfile.txt") {
                                if (it != null) {
                                    lifecycleScope.launch {
                                        viewModel.newTab(it, checkDuplicate = true, switchToTab = true)
                                    }
                                }
                            }
                        }
                    }
                }

                AddDialogItem(icon = drawables.file_symlink, title = stringResource(strings.open_file)) {
                    addDialog = false
                    MainActivity.instance?.apply {
                        fileManager.requestOpenFile(mimeType = "*/*") {
                            if (it != null) {
                                lifecycleScope.launch {
                                    viewModel.newTab(
                                        it.toFileObject(expectedIsFile = true),
                                        checkDuplicate = true,
                                        switchToTab = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (tempFileNameDialog) {
        var fileName by remember { mutableStateOf("untitled.txt") }

        fun getUniqueFileName(baseName: String): String {
            val tempDir = getTempDir().child("temp_editor")
            val extension = baseName.substringAfterLast('.', "")
            val nameWithoutExt = baseName.substringBeforeLast('.', baseName)

            // Check if base name is available
            if (!tempDir.child(baseName).exists()) {
                return baseName
            }

            // Find next available number
            var counter = 1
            var uniqueName: String
            do {
                uniqueName =
                    if (extension.isNotEmpty()) {
                        "${nameWithoutExt}${counter}.${extension}"
                    } else {
                        "${nameWithoutExt}${counter}"
                    }
                counter++
            } while (tempDir.child(uniqueName).exists())

            return uniqueName
        }

        fun getUniqueTempFile(): FileObject {
            val uniqueName = getUniqueFileName(fileName)
            fileName = uniqueName // Update the state with the unique name

            // do not change getTempDir().child("temp_editor") it used for checking in editor tab
            return FileWrapper(getTempDir().child("temp_editor").child(uniqueName))
        }

        val tempFile = getUniqueTempFile()

        SingleInputDialog(
            title = stringResource(strings.temp_file),
            inputValue = fileName,
            onInputValueChange = { fileName = it },
            onConfirm = {
                DefaultScope.launch(Dispatchers.IO) {
                    tempFileNameDialog = false
                    tempFile.createFileIfNot()
                    viewModel.newTab(tempFile, switchToTab = true)
                }
            },
            onDismiss = { tempFileNameDialog = false },
            singleLineMode = true,
            confirmText = stringResource(strings.ok),
            inputLabel = stringResource(strings.file_name),
        )
    }
}
