package com.rk.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.activities.terminal.Terminal
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.openWith
import com.rk.file.to_save_file
import com.rk.filetree.FileTreeTab
import com.rk.filetree.FileTreeViewModel
import com.rk.filetree.addProject
import com.rk.filetree.getAppropriateName
import com.rk.filetree.removeProject
import com.rk.filetree.tabs
import com.rk.icons.CreateNewFile
import com.rk.icons.CreateNewFolder
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.fillPlaceholders
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.app.InbuiltFeatures
import com.rk.tabs.editor.EditorTab
import com.rk.utils.errorDialog
import com.rk.utils.showTerminalNotice
import com.rk.utils.toast
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.io.Util.copyStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionDialog(
    modifier: Modifier = Modifier,
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
                    icon = if (file.isFile()) drawables.copy else drawables.paste,
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
                                val clipboardFile = FileOperations.clipboard!!
                                val clipboardParentFile = clipboardFile.getParentFile()
                                pasteFile(context, clipboardFile, file, isCut = FileOperations.isCut)
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
                        FileOperations.saveAs(context, file)
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

                if (fileTreeContext && file.isDirectory() && !tabs.any { it is FileTreeTab && it.root == file }) {
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
                    val success = file.renameTo(newName)

                    if (!success) {
                        toast(strings.rename_failed)
                        return@launch
                    }

                    val parentFile = file.getParentFile() ?: return@launch
                    fileTreeViewModel.updateCache(parentFile)

                    MainActivity.instance?.apply {
                        val targetTab = viewModel.tabs.find { it is EditorTab && it.file == file } as? EditorTab

                        targetTab?.tabTitle?.value = newName
                        targetTab?.file = parentFile.getChildForName(newName)
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
                    val success = FileOperations.deleteFile(file)

                    if (!success) {
                        toast(strings.delete_failed)
                        return@launch
                    }

                    val parentFile = file.getParentFile()
                    if (parentFile != null) {
                        fileTreeViewModel.updateCache(file.getParentFile()!!)
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
                                    MainActivity.instance?.viewModel?.newTab(newChild)
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

// File Operations utility class
object FileOperations {
    var clipboard: FileObject? = null
    var isCut: Boolean = false

    suspend fun copyToClipboard(file: FileObject, isCut: Boolean = false) {
        clipboard = file
        this.isCut = isCut
    }

    suspend fun deleteFile(file: FileObject): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            errorDialog(e)
            false
        }
    }

    suspend fun openWithExternalApp(context: Context, file: FileObject) {
        openWith(context, file)
    }

    fun saveAs(context: Context, file: FileObject) {
        to_save_file = file
        MainActivity.instance?.fileManager?.requestOpenDirectoryToSaveFile(file)
    }

    fun addFile(parentFile: FileObject) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        MainActivity.instance?.fileManager?.requestAddFile(parentFile)
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

data class ContentProgress(val totalSize: Long, val totalItems: Long)

private suspend fun calculateContent(folder: FileObject, onProgress: (ContentProgress) -> Unit = {}) {
    var totalSize = 0L
    var totalItems = 0L

    val stack = ArrayDeque<FileObject>()
    stack.add(folder)

    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        if (current.isDirectory()) {
            stack.addAll(current.listFiles())
            totalItems++
            onProgress(ContentProgress(totalSize, totalItems))
        } else {
            totalSize += current.length()
            totalItems++
            onProgress(ContentProgress(totalSize, totalItems))
        }
    }
}

enum class PropertyRoutes(val label: String, val route: String) {
    GENERAL(strings.general.getString(), "general"),
    ADVANCED(strings.advanced.getString(), "advanced"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesDialog(file: FileObject, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = 0) { PropertyRoutes.entries.size }
    val scope = rememberCoroutineScope()

    val scrollStates = PropertyRoutes.entries.map { rememberScrollState() }
    val currentScrollState = scrollStates[pagerState.currentPage]
    val showHorizontalDivider by
        remember(pagerState.currentPage) { derivedStateOf { currentScrollState.canScrollForward } }

    XedDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            Text(
                text = stringResource(strings.properties),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
            )

            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                PropertyRoutes.entries.forEachIndexed { index, destination ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(text = destination.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = PropertyRoutes.entries.size,
                pageSpacing = 16.dp,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollStates[page]).padding(all = 24.dp),
                ) {
                    when (PropertyRoutes.entries[page]) {
                        PropertyRoutes.GENERAL -> GeneralProperties(file)
                        PropertyRoutes.ADVANCED -> AdvancedProperties(file)
                    }
                }
            }

            if (showHorizontalDivider) HorizontalDivider()

            Box(modifier = Modifier.padding(start = 24.dp, bottom = 24.dp, end = 24.dp).align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(text = stringResource(strings.close)) }
            }
        }
    }
}

@Composable
fun GeneralProperties(file: FileObject) {
    var size by remember { mutableStateOf(formatFileSize(0)) }
    var itemsCount by remember { mutableStateOf("0") }
    val numberFormatter = remember { NumberFormat.getNumberInstance() }

    val lastModified =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault())
            .format(Date(file.lastModified()))

    LaunchedEffect(file) {
        if (file.isFile()) {
            size = formatFileSize(file.length())
        } else {
            calculateContent(file) {
                size = formatFileSize(it.totalSize)
                itemsCount = numberFormatter.format(it.totalItems)
            }
        }
    }

    InfoRow(stringResource(strings.name), file.getName())
    InfoRow(stringResource(strings.path), file.getAbsolutePath())
    InfoRow(
        label = stringResource(strings.type),
        value =
            if (file.isDirectory()) {
                stringResource(strings.folder)
            } else {
                stringResource(strings.file)
            },
    )
    if (file.isDirectory()) {
        InfoRow(
            label = stringResource(strings.content),
            value = stringResource(strings.content_property).fillPlaceholders(itemsCount, size),
        )
    } else {
        InfoRow(label = stringResource(strings.size), value = size)
    }
    InfoRow(stringResource(strings.last_modified), lastModified)
}

@Composable
fun AdvancedProperties(file: FileObject) {
    //    PermissionRow(canRead = file.canRead(), canWrite = file.canWrite(), canExecute = file.canExecute())
    InfoRow(stringResource(strings.permissions), getPseudoPermissions(file))
    if (InbuiltFeatures.debugMode.state.value) {
        InfoRow(stringResource(strings.wrapper_type), file.javaClass.simpleName)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    OutlinedTextField(value = value, onValueChange = {}, label = { Text(label) }, readOnly = true)
}

fun getPseudoPermissions(file: FileObject): String {
    val type =
        when {
            file.isDirectory() -> "d"
            file.isSymlink() -> "l"
            else -> "-"
        }

    val r = if (file.canRead()) "r" else "-"
    val w = if (file.canWrite()) "w" else "-"
    val x = if (file.canExecute()) "x" else "-"
    return "$type$r$w$x"
}

// Utility functions
private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(), "%.1f GB", gb)
}

/**
 * Pastes a file or folder to the destination directory
 *
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
    onProgress: ((String) -> Unit)? = null,
): Result<Unit> =
    withContext(Dispatchers.IO) {
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

/** Recursively copies a file or directory */
private suspend fun copyRecursive(
    context: Context,
    sourceFile: FileObject,
    targetParent: FileObject,
    onProgress: ((String) -> Unit)?,
) {
    onProgress?.invoke("Processing: ${sourceFile.getName()}")

    if (sourceFile.isDirectory()) {
        // Create target directory
        val targetDir =
            targetParent.createChild(false, sourceFile.getName())
                ?: throw IllegalStateException("Failed to create directory: ${sourceFile.getName()}")

        // Copy all children
        sourceFile.listFiles().forEach { child -> copyRecursive(context, child, targetDir, onProgress) }
    } else {
        // Copy file content
        val targetFile =
            targetParent.createChild(true, sourceFile.getName())
                ?: throw IllegalStateException("Failed to create file: ${sourceFile.getName()}")

        context.contentResolver.openInputStream(sourceFile.toUri())?.use { inputStream ->
            context.contentResolver.openOutputStream(targetFile.toUri())?.use { outputStream ->
                copyStream(inputStream, outputStream)
            } ?: throw IllegalStateException("Failed to open output stream for: ${sourceFile.getName()}")
        } ?: throw IllegalStateException("Failed to open input stream for: ${sourceFile.getName()}")
    }
}

/** Checks if parentDir is a parent of childDir (prevents copying directory into itself) */
private suspend fun isParentOf(parentDir: FileObject, childDir: FileObject): Boolean {
    var current: FileObject? = childDir
    while (current != null) {
        if (current == parentDir) {
            return true
        }
        current = current.getParentFile()
    }
    return false
}

/** Convenience function for copying files */
suspend fun copyFile(
    context: Context,
    sourceFile: FileObject,
    destinationFolder: FileObject,
    onProgress: ((String) -> Unit)? = null,
): Result<Unit> = pasteFile(context, sourceFile, destinationFolder, isCut = false, onProgress)

/** Convenience function for moving files */
suspend fun moveFile(
    context: Context,
    sourceFile: FileObject,
    destinationFolder: FileObject,
    onProgress: ((String) -> Unit)? = null,
): Result<Unit> = pasteFile(context, sourceFile, destinationFolder, isCut = true, onProgress)
