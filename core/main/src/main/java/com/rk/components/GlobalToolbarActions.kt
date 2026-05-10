package com.rk.components

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.rk.ai.GeminiBridge
import com.rk.commands.CommandProvider
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.sandboxHomeDir
import com.rk.file.toFileObject
import com.rk.filetree.FileTreeTab
import com.rk.filetree.drawerTabs
import com.rk.filetree.currentDrawerTab
import com.rk.icons.CreateNewFile
import com.rk.icons.XedIcon
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.search.CodeSearchDialog
import com.rk.search.FileSearchDialog
import com.rk.settings.Settings
import com.rk.settings.app.InbuiltFeatures
import com.rk.tabs.editor.GeminiCliSheet
import com.rk.tabs.editor.createGeminiSheetSession
import com.rk.utils.application
import com.rk.utils.errorDialog
import com.rk.utils.getTempDir
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

var addDialog by mutableStateOf(false)
var fileSearchDialog by mutableStateOf(false)
var codeSearchDialog by mutableStateOf(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalToolbarActions(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tempFileNameDialog by remember { mutableStateOf(false) }
    var showHomeGeminiSheet by remember { mutableStateOf(false) }
    var homeGeminiSession by remember { mutableStateOf<TerminalSession?>(null) }
    var homeGeminiCwd by remember { mutableStateOf<String?>(null) }

    if (viewModel.tabs.isEmpty() || viewModel.currentTab?.showGlobalActions == true) {
        val newFileCommand = CommandProvider.NewFileCommand
        val terminalCommand = CommandProvider.TerminalCommand
        val geminiCliCommand = CommandProvider.GeminiCliCommand
        val settingsCommand = CommandProvider.SettingsCommand

        IconButton(onClick = { newFileCommand.action(ActionContext(context as Activity)) }) {
            XedIcon(newFileCommand.getIcon())
        }

        if (InbuiltFeatures.terminal.state.value) {
            IconButton(onClick = { terminalCommand.action(ActionContext(context as Activity)) }) {
                XedIcon(terminalCommand.getIcon())
            }

            IconButton(onClick = { showHomeGeminiSheet = true }) {
                XedIcon(geminiCliCommand.getIcon())
            }
        }

        IconButton(onClick = { settingsCommand.action(ActionContext(context as Activity)) }) {
            XedIcon(settingsCommand.getIcon())
        }
    }

    if (showHomeGeminiSheet) {
        HomeGeminiSheet(
            viewModel = viewModel,
            session = homeGeminiSession,
            sessionCwd = homeGeminiCwd,
            onSessionChange = { homeGeminiSession = it },
            onSessionCwdChange = { homeGeminiCwd = it },
            onDismiss = { showHomeGeminiSheet = false },
        )
    }

    if (fileSearchDialog && currentDrawerTab is FileTreeTab) {
        FileSearchDialog(
            searchViewModel = searchViewModel.get()!!,
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
                        drawerStateRef.get()!!.open()
                    }
                }
            },
        )
    }

    if (codeSearchDialog && currentDrawerTab is FileTreeTab) {
        CodeSearchDialog(
            mainViewModel = viewModel,
            searchViewModel = searchViewModel.get()!!,
            projectFile = (currentDrawerTab as FileTreeTab).root,
            onFinish = { codeSearchDialog = false },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeGeminiSheet(
    viewModel: MainViewModel,
    session: TerminalSession?,
    sessionCwd: String?,
    onSessionChange: (TerminalSession?) -> Unit,
    onSessionCwdChange: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val homeDir = if (Settings.sandbox) "/home" else sandboxHomeDir().absolutePath
    val projectDir = ((currentDrawerTab as? FileTreeTab)?.root as? FileWrapper)?.getAbsolutePath()
        ?: drawerTabs.filterIsInstance<FileTreeTab>().mapNotNull { it.root as? FileWrapper }.firstOrNull()?.getAbsolutePath()
    val defaultDir = sessionCwd ?: projectDir ?: homeDir

    fun startGemini(workingDir: String = defaultDir, extraArgs: List<String> = emptyList()) {
        val currentActivity = activity ?: return
        session?.finishIfRunning()
        onSessionChange(null)
        scope.launch {
            val bridge = withContext(Dispatchers.IO) { GeminiBridge.ensureStarted(viewModel, workingDir) }
            val newSession = createGeminiSheetSession(
                activity = currentActivity,
                bridge = bridge,
                workingDir = workingDir,
                extraArgs = extraArgs,
            )
            onSessionChange(newSession)
            onSessionCwdChange(workingDir)
        }
    }

    LaunchedEffect(Unit) {
        if (session == null || !session.isRunning) startGemini(defaultDir)
    }

    GeminiCliSheet(
        onDismissRequest = onDismiss,
        cwd = defaultDir,
        session = session,
        controls = {
            TextButton(onClick = { startGemini(defaultDir) }) { Text("Restart") }
            TextButton(onClick = { startGemini(defaultDir, listOf("--prompt-interactive", "/auth")) }) { Text("Auth") }
            TextButton(onClick = {
                session?.finishIfRunning()
                onSessionChange(null)
                onSessionCwdChange(null)
            }) { Text("Stop") }
        },
    )
}
