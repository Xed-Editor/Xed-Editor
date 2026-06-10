package com.rk.components

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.drawerStateRef
import com.rk.activities.main.fileTreeViewModel
import com.rk.activities.main.searchViewModel
import com.rk.commands.ActionContext
import com.rk.commands.Command
import com.rk.commands.CommandProvider
import com.rk.drawer.DrawerViewModel
import com.rk.extension.XedExtensionPoint
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.toFileObject
import com.rk.filetree.FileTreeTab
import com.rk.icons.CreateNewFile
import com.rk.icons.XedIcon
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.search.CodeSearchDialog
import com.rk.search.FileSearchDialog
import com.rk.settings.app.InbuiltFeatures
import com.rk.utils.application
import com.rk.utils.errorDialog
import com.rk.utils.getTempDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

var addDialog by mutableStateOf(false)
var fileSearchDialog by mutableStateOf(false)
var codeSearchDialog by mutableStateOf(false)

object GlobalActionManager {
    private var _commands: SnapshotStateList<Command>? = null

    val commands: SnapshotStateList<Command>
        get() {
            if (_commands == null) {
                _commands =
                    mutableStateListOf(
                        CommandProvider.NewFileCommand,
                        CommandProvider.TerminalCommand,
                        CommandProvider.SettingsCommand,
                    )
            }
            return _commands!!
        }

    @XedExtensionPoint
    fun addCommand(command: Command, index: Int = -1) {
        val cmds = commands
        val existingCommand = cmds.find { it.id == command.id }
        if (existingCommand != null) {
            val oldIndex = cmds.indexOf(existingCommand)
            cmds.removeAt(oldIndex)
            val targetIndex = if (index != -1) index.coerceIn(0, cmds.size) else oldIndex
            cmds.add(targetIndex.coerceIn(0, cmds.size), command)
            return
        }

        if (index in 0..cmds.size) {
            cmds.add(index, command)
        } else {
            cmds.add(command)
        }
    }

    @XedExtensionPoint
    fun removeCommand(command: Command) {
        commands.removeIf { it.id == command.id }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalToolbarActions(viewModel: MainViewModel, drawerViewModel: DrawerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tempFileNameDialog by remember { mutableStateOf(false) }

    val commands by remember { derivedStateOf { GlobalActionManager.commands.toList() } }

    if (viewModel.tabs.isEmpty() || viewModel.currentTab?.showGlobalActions == true) {
        for (command in commands) {
            if (command == CommandProvider.TerminalCommand) {
                if (InbuiltFeatures.terminal.state.value) {
                    IconButton(onClick = { command.action(ActionContext(context as Activity)) }) {
                        XedIcon(command.getIcon())
                    }
                }
            } else {
                IconButton(onClick = { command.action(ActionContext(context as Activity)) }) {
                    XedIcon(command.getIcon())
                }
            }
        }
    }

    if (fileSearchDialog && drawerViewModel.currentDrawerTab is FileTreeTab) {
        FileSearchDialog(
            mainViewModel = viewModel,
            searchViewModel = searchViewModel.get()!!,
            projectFile = (drawerViewModel.currentDrawerTab as FileTreeTab).root,
            onFinish = { fileSearchDialog = false },
            onSelect = { projectFile, fileObject ->
                scope.launch {
                    if (fileObject.isFile()) {
                        viewModel.editorManager.openFile(
                            fileObject = fileObject,
                            projectRoot = projectFile,
                            checkDuplicate = true,
                            switchToTab = true,
                        )
                        drawerStateRef.get()?.close()
                    } else {
                        fileTreeViewModel.get()?.goToFolder(projectFile, fileObject)
                        drawerStateRef.get()!!.open()
                    }
                }
            },
        )
    }

    if (codeSearchDialog && drawerViewModel.currentDrawerTab is FileTreeTab) {
        CodeSearchDialog(
            mainViewModel = viewModel,
            searchViewModel = searchViewModel.get()!!,
            projectFile = (drawerViewModel.currentDrawerTab as FileTreeTab).root,
            onFinish = { codeSearchDialog = false },
        )
    }

    if (addDialog) {
        ModalBottomSheet(onDismissRequest = { addDialog = false }) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)) {
                AddDialogItem(resId = drawables.file, title = stringResource(strings.temp_file)) {
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
                                        viewModel.editorManager.openFile(
                                            it,
                                            projectRoot = null,
                                            checkDuplicate = true,
                                            switchToTab = true,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AddDialogItem(resId = drawables.file_symlink, title = stringResource(strings.open_file)) {
                    addDialog = false
                    MainActivity.instance?.apply {
                        fileManager.requestOpenFile(mimeType = "*/*") {
                            if (it != null) {
                                lifecycleScope.launch {
                                    viewModel.editorManager.openFile(
                                        it.toFileObject(expectedIsFile = true),
                                        checkDuplicate = true,
                                        projectRoot = null,
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
                    viewModel.editorManager.openFile(tempFile, projectRoot = null, switchToTab = true)
                }
            },
            onDismiss = { tempFileNameDialog = false },
            singleLineMode = true,
            confirmText = stringResource(strings.ok),
            inputLabel = stringResource(strings.file_name),
        )
    }
}
