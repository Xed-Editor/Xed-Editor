package com.rk.filetree

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.*
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.UriWrapper
import com.rk.DefaultScope
import com.rk.file.child
import com.rk.file.sandboxHomeDir
import com.rk.file.toFileObject
import com.rk.utils.application
import com.rk.utils.dialog
import com.rk.utils.errorDialog
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.activities.main.MainActivity
import com.rk.components.AddDialogItem
import com.rk.components.CloseConfirmationDialog
import com.rk.components.FileActionDialog
import com.rk.settings.app.InbuiltFeatures
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream


data class FileObjectWrapper(val fileObject: FileObject, val name: String) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (other is FileObject) {
            return other == fileObject
        }
        if (other !is FileObjectWrapper) {
            return false
        }
        return other.fileObject == fileObject
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + fileObject.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}


private val mutex = Mutex()

suspend fun saveProjects() {
    mutex.withLock {
        withContext(Dispatchers.IO) {
            currentProject?.getAbsolutePath()?.let { Settings.selectedProject = it }

            val file = application!!.cacheDir.child("projects")

            ObjectOutputStream(FileOutputStream(file)).use { oos ->
                oos.writeObject(projects.map { it.fileObject })
            }
        }
    }
}

suspend fun restoreProjects() {
    mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val file = application!!.cacheDir.child("projects")
                if (file.exists() && file.canRead()) {
                    ObjectInputStream(FileInputStream(file)).use { ois ->
                        val list = mutableStateListOf<FileObjectWrapper>()
                        list.addAll((ois.readObject() as List<FileObject>).map { FileObjectWrapper(it, it.getName()) })
                        withContext(Dispatchers.Main){
                            projects = list
                        }

                    }
                }
                projects.forEach{
                    if (it.fileObject.getAbsolutePath() == Settings.selectedProject){
                        currentProject = it.fileObject
                    }
                }

            }.onFailure { it.printStackTrace();errorDialog(it) }
        }
    }

}


var projects = mutableStateListOf<FileObjectWrapper>()
var currentProject by mutableStateOf<FileObject?>(null)

@OptIn(DelicateCoroutinesApi::class)
fun addProject(fileObject: FileObject, save: Boolean = false) {
    if (projects.find { it.fileObject == fileObject } != null) {
        return
    }
    projects.add(FileObjectWrapper(fileObject = fileObject, name = fileObject.getName()))
    currentProject = fileObject
    if (save) {
        GlobalScope.launch(Dispatchers.IO) {
            saveProjects()
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun removeProject(fileObject: FileObject, save: Boolean = false) {
    projects.remove(projects.find { it.fileObject == fileObject })
    if (currentProject == fileObject) {
        currentProject = if (projects.size - 1 >= 0) {
            projects[projects.size - 1].fileObject
        } else {
            null
        }
    }
    if (save) {
        GlobalScope.launch(Dispatchers.IO) {
            saveProjects()
        }
    }
}

var isLoading by mutableStateOf(true)


@Composable
fun DrawerContent(modifier: Modifier = Modifier,onFileSelected:(FileObject)-> Unit,fileTreeViewModel: FileTreeViewModel) {
    val context = LocalContext.current

    val openFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                runCatching {
                    // Persist access permissions (required for Android 5.0+)
                    context.contentResolver.takePersistableUriPermission(it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }.onFailure { it.printStackTrace() }
                
                addProject(it.toFileObject(expectedIsFile = false))
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxSize()) {
                val scope = rememberCoroutineScope()
                var showAddDialog by rememberSaveable { mutableStateOf(false) }
                var fileActionDialog by remember { mutableStateOf<FileObject?>(null) }
                var closeProjectDialog by remember { mutableStateOf(false) }

                NavigationRail(modifier = Modifier.width(61.dp)) {
                    projects.forEach { file ->
                        NavigationRailItem(
                            selected = file.fileObject == currentProject,
                            icon = {
                                val iconId =
                                    if ((file.fileObject is UriWrapper && file.fileObject.isTermuxUri()) || (file.fileObject is FileWrapper && file.fileObject.file == sandboxHomeDir())) {
                                        drawables.terminal
                                    } else {
                                        drawables.outline_folder
                                    }
                                Icon(painter = painterResource(iconId), contentDescription = null)
                            },
                            onClick = {
                                if (file.fileObject == currentProject) {
                                    closeProjectDialog = true
                                } else {
                                    scope.launch {
                                        currentProject = file.fileObject
                                    }
                                }

                            },
                            label = {
                                Text(
                                    file.fileObject.getAppropriateName(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            })
                    }

                    NavigationRailItem(selected = false, icon = {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                    }, onClick = {
                        showAddDialog = true
                    }, label = {
                        Text(stringResource(strings.add))
                    })
                }

                VerticalDivider()

                Crossfade(targetState = currentProject, label = "file tree") { project ->
                    if (project != null) {
                        FileTree(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                                .systemBarsPadding(),
                            rootNode = project.toFileTreeNode(),
                            viewModel = fileTreeViewModel,
                            onFileClick = {
                                if (it.isFile) {
                                    onFileSelected.invoke(it.file)
                                }
                            },
                            onFileLongClick = {
                                fileActionDialog = it.file
                            })
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(drawables.outline_folder),
                                contentDescription = null, tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(strings.no_folder_opened),color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                }

                if (showAddDialog) {
                    AddProjectDialog(
                        onDismiss = { showAddDialog = false },
                        openFolder = openFolder,
                        onAddProject = { fileObject ->
                            scope.launch { addProject(fileObject, true) }
                        },
                        showPrivateFileWarning = { callback ->
                            dialog(title = strings.attention.getString(), msg = strings.warning_private_dir.getString(), onOk = {
                                callback.invoke()
                            })
                        }
                    )
                }

                if (fileActionDialog != null && currentProject != null){
                    FileActionDialog(modifier = Modifier, file = fileActionDialog!!, root = currentProject!!, onDismissRequest = {
                        fileActionDialog = null
                    }, fileTreeViewModel = fileTreeViewModel)
                }

                if (closeProjectDialog) {
                    CloseConfirmationDialog(
                        projectName = currentProject!!.getAppropriateName(),
                        onConfirm = {
                            closeProjectDialog = false
                            currentProject?.let { removeProject(it) }
                        }, onDismiss = {
                            closeProjectDialog = false
                        }
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProjectDialog(
    onDismiss: () -> Unit,
    onAddProject: (FileObject) -> Unit,
    openFolder: ManagedActivityResultLauncher<Uri?, Uri?>,
    showPrivateFileWarning:(onOK:()-> Unit)-> Unit
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val lifecycleScope = remember { activity?.lifecycleScope ?: DefaultScope }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)
        ) {
            AddDialogItem(
                icon = drawables.file_symlink,
                title = stringResource(strings.open_directory),
                description = stringResource(strings.open_dir_desc),
                onClick = {
                    openFolder.launch(null)
                    onDismiss()
                }
            )

            // Open Path option
            val is11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            val isManager = is11Plus && Environment.isExternalStorageManager()
            val legacyPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED

            val storage = Environment.getExternalStorageDirectory()
            if (((is11Plus && isManager) || (!is11Plus && legacyPermission)) && storage.canWrite() && storage.canRead() ) {
                AddDialogItem(
                    icon = drawables.android,
                    title = stringResource(strings.open_path),
                    description = stringResource(strings.open_path_desc),
                    onClick = {
                        addProject(FileWrapper(storage))
                        onDismiss()
                    }
                )
            }

            if (InbuiltFeatures.debugMode.state.value) {
                AddDialogItem(
                    icon = drawables.build,
                    title = stringResource(strings.private_files),
                    description = stringResource(strings.private_files_desc),
                    onClick = {
                        if (!Settings.has_shown_private_data_dir_warning) {
                            showPrivateFileWarning {
                                Settings.has_shown_private_data_dir_warning = true
                                lifecycleScope.launch {
                                    onAddProject(FileWrapper(activity!!.filesDir.parentFile!!))
                                }
                            }
                        } else {
                            lifecycleScope.launch {
                                onAddProject(FileWrapper(activity!!.filesDir.parentFile!!))
                            }
                        }
                        onDismiss()
                    }
                )
            }

            // Terminal Home option
            AddDialogItem(
                icon = drawables.terminal,
                title = stringResource(strings.terminal_home),
                description = stringResource(strings.terminal_home_desc),
                onClick = {
                    if (!Settings.has_shown_terminal_dir_warning) {
                        showPrivateFileWarning {
                            Settings.has_shown_terminal_dir_warning = true
                            lifecycleScope.launch {
                                onAddProject(FileWrapper(sandboxHomeDir()))
                            }
                        }
                    } else {
                        lifecycleScope.launch {
                            onAddProject(FileWrapper(sandboxHomeDir()))
                        }
                    }
                    onDismiss()
                }
            )
        }
    }
}





