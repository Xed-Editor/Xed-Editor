package com.rk.components

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.drawerStateRef
import com.rk.activities.main.fileTreeViewModel
import com.rk.activities.main.searchViewModel
import com.rk.commands.ActionContext
import com.rk.commands.CommandProvider
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.toFileObject
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentDrawerTab
import com.rk.icons.CreateNewFile
import com.rk.icons.XedIcon
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.search.CodeSearchDialog
import com.rk.search.FileSearchDialog
import com.rk.settings.app.InbuiltFeatures
import com.rk.templates.FileTemplate
import com.rk.templates.FileTemplateManager
import com.rk.utils.application
import com.rk.utils.errorDialog
import com.rk.utils.getTempDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

var addDialog by mutableStateOf(false)
var fileSearchDialog by mutableStateOf(false)
var codeSearchDialog by mutableStateOf(false)
var showTemplatePicker by mutableStateOf(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalToolbarActions(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tempFileNameDialog by remember { mutableStateOf(false) }

    if (viewModel.tabs.isEmpty() || viewModel.currentTab?.showGlobalActions == true) {
        val newFileCommand = CommandProvider.NewFileCommand
        val terminalCommand = CommandProvider.TerminalCommand
        val aiCliCommand = CommandProvider.AiCliCommand
        val settingsCommand = CommandProvider.SettingsCommand

        IconButton(
            onClick = { newFileCommand.action(ActionContext(context as Activity)) },
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            XedIcon(newFileCommand.getIcon(), modifier = Modifier.size(22.dp))
        }

        if (InbuiltFeatures.terminal.state.value) {
            IconButton(
                onClick = { 
                    viewModel.bottomPanelMode = com.rk.activities.main.BottomPanelMode.TERMINAL
                    viewModel.showBottomPanel = true 
                },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                XedIcon(terminalCommand.getIcon(), modifier = Modifier.size(22.dp))
            }

            IconButton(
                onClick = { 
                    viewModel.bottomPanelMode = com.rk.activities.main.BottomPanelMode.AI
                    viewModel.showBottomPanel = true 
                },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                XedIcon(aiCliCommand.getIcon(), modifier = Modifier.size(22.dp))
            }
        }

        IconButton(
            onClick = { settingsCommand.action(ActionContext(context as Activity)) },
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            XedIcon(settingsCommand.getIcon(), modifier = Modifier.size(22.dp))
        }
    }

    if (fileSearchDialog && currentDrawerTab is FileTreeTab) {
        val searchVm = searchViewModel.get() ?: return
        FileSearchDialog(
            searchViewModel = searchVm,
            projectFile = (currentDrawerTab as FileTreeTab).root,
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
                        drawerStateRef.get()?.open()
                    }
                }
            },
        )
    }

    if (codeSearchDialog && currentDrawerTab is FileTreeTab) {
        val searchVm = searchViewModel.get() ?: return
        CodeSearchDialog(
            mainViewModel = viewModel,
            searchViewModel = searchVm,
            projectFile = (currentDrawerTab as FileTreeTab).root,
            onFinish = { codeSearchDialog = false },
        )
    }

    if (addDialog) {
        XedBottomSheet(onDismissRequest = { addDialog = false }) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
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
                    showTemplatePicker = true
                }

                AddDialogItem(icon = drawables.file_symlink, title = stringResource(strings.open_file)) {
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

    if (showTemplatePicker) {
        TemplatePickerDialog(
            onTemplateSelected = { template ->
                showTemplatePicker = false
                createFileFromTemplate(template, viewModel)
            },
            onDismiss = { showTemplatePicker = false },
        )
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
                tempFileNameDialog = false
                DefaultScope.launch(Dispatchers.IO) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePickerDialog(
    onTemplateSelected: (FileTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    val templatesByCategory = remember { FileTemplateManager.getByCategory() }

    XedBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "New File from Template",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.height(400.dp)) {
                templatesByCategory.forEach { (category, templates) ->
                    item {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(templates) { template ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTemplateSelected(template) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = ".${template.extension}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun createFileFromTemplate(template: FileTemplate, viewModel: MainViewModel) {
    val activity = MainActivity.instance ?: return
    val projectRoot = (currentDrawerTab as? FileTreeTab)?.root

    activity.apply {
        fileManager.createNewFile(mimeType = template.mimeType, title = "newfile.${template.extension}") {
            if (it != null) {
                lifecycleScope.launch {
                    // Write template content
                    if (template.content.isNotEmpty()) {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            it.writeText(template.content)
                        }
                    }
                    viewModel.editorManager.openFile(
                        it,
                        projectRoot = projectRoot,
                        checkDuplicate = true,
                        switchToTab = true,
                    )
                }
            }
        }
    }
}
