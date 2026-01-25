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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.fileTreeViewModel
import com.rk.components.AddDialogItem
import com.rk.components.CloseConfirmationDialog
import com.rk.components.DoubleInputDialog
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.sandboxHomeDir
import com.rk.file.toFileObject
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.app.InbuiltFeatures
import com.rk.utils.application
import com.rk.utils.dialog
import com.rk.utils.readObject
import com.rk.utils.toast
import com.rk.utils.writeObject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val mutex = Mutex()

suspend fun saveProjects() {
    mutex.withLock {
        val file = FileWrapper(application!!.filesDir.child("projects"))
        val serializableList = ArrayList(tabs)
        file.writeObject(serializableList)

        val currentTabFile = FileWrapper(application!!.filesDir.child("currentTab"))
        if (currentTab != null) {
            currentTabFile.writeObject(currentTab!!)
        } else {
            currentTabFile.delete()
        }

        val expandedNodeFile = FileWrapper(application!!.filesDir.child("expanded_filetree_nodes"))
        fileTreeViewModel.get()?.getExpandedNodes()?.let { expandedNodeFile.writeObject(it) }
    }
}

suspend fun restoreProjects() {
    mutex.withLock {
        runCatching {
            val loadedTabs =
                withContext(Dispatchers.IO) {
                    val file = FileWrapper(application!!.filesDir.child("projects"))

                    if (file.exists() && file.canRead()) {
                        file.readObject() as? ArrayList<DrawerTab> ?: emptyList()
                    } else {
                        emptyList()
                    }
                }

            // Update the existing state list on Main thread
            withContext(Dispatchers.Main) {
                tabs.clear()
                tabs.addAll(loadedTabs)
            }

            val currentTabFile = FileWrapper(application!!.filesDir.child("currentTab"))
            if (currentTabFile.exists() && currentTabFile.canRead()) {
                currentTab = currentTabFile.readObject() as DrawerTab
            }

            val expandedNodeFile = FileWrapper(application!!.filesDir.child("expanded_filetree_nodes"))
            if (expandedNodeFile.exists() && expandedNodeFile.canRead()) {
                fileTreeViewModel.get()?.setExpandedNodes(expandedNodeFile.readObject() as Map<FileObject, Boolean>)
            }
        }
            .onFailure {
                it.printStackTrace()
                toast(strings.project_restore_failed)
            }
    }
}

var tabs = mutableStateListOf<DrawerTab>()
var currentTab by mutableStateOf<DrawerTab?>(null)

@OptIn(DelicateCoroutinesApi::class)
fun addProject(fileObject: FileObject, save: Boolean = false) {
    val alreadyExistingProject = tabs.find { it is FileTreeTab && it.root == fileObject }
    if (alreadyExistingProject != null) {
        currentTab = alreadyExistingProject
        return
    }
    val tab = FileTreeTab(fileObject)
    tabs.add(tab)
    currentTab = tab
    if (save) {
        GlobalScope.launch(Dispatchers.IO) { saveProjects() }
    }
}

fun addProject(tab: DrawerTab, save: Boolean = false) {
    tabs.add(tab)
    currentTab = tab
    if (save) {
        GlobalScope.launch(Dispatchers.IO) { saveProjects() }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun removeProject(fileObject: FileObject, save: Boolean = false) {
    val tabToRemove = tabs.find { it is FileTreeTab && it.root == fileObject } ?: return

    if (currentTab == tabToRemove) {
        currentTab = tabs.firstOrNull { it != tabToRemove }
    }

    tabs.remove(tabToRemove)

    if (save) {
        GlobalScope.launch(Dispatchers.IO) { saveProjects() }
    }
}

fun removeProject(tab: DrawerTab, save: Boolean = false) {
    if (currentTab == tab) {
        currentTab = tabs.firstOrNull { it != tab }
    }

    tabs.remove(tab)

    if (save) {
        GlobalScope.launch(Dispatchers.IO) { saveProjects() }
    }
}

fun validateValue(value: String): String? {
    return when {
        value.isBlank() -> {
            strings.value_empty_err.getString()
        }

        else -> null
    }
}

var isLoading by mutableStateOf(true)

@Composable
fun DrawerContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val openFolder =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { uri ->
                uri?.let {
                    runCatching {
                        // Persist access permissions (required for Android 5.0+)
                        context.contentResolver.takePersistableUriPermission(
                            it,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }
                        .onFailure { it.printStackTrace() }

                    scope.launch { addProject(it.toFileObject(expectedIsFile = false)) }
                }
            },
        )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxSize()) {
                val scope = rememberCoroutineScope()
                var showAddDialog by rememberSaveable { mutableStateOf(false) }

                var closeProjectDialog by remember { mutableStateOf(false) }
                var showGitCloneDialog by remember { mutableStateOf(false) }

                var repoURL by remember { mutableStateOf("") }
                var repoBranch by remember { mutableStateOf("main") }

                var repoURLError by remember { mutableStateOf<String?>(null) }
                var repoBranchError by remember { mutableStateOf<String?>(null) }

                NavigationRail(modifier = Modifier.width(61.dp)) {
                    tabs.forEach { tab ->
                        NavigationRailItem(
                            selected = currentTab == tab,
                            icon = {
                                when (val icon = tab.getIcon()) {
                                    is Icon.DrawableRes -> {
                                        Icon(painter = painterResource(icon.drawableRes), contentDescription = null)
                                    }

                                    is Icon.VectorIcon -> {
                                        Icon(imageVector = icon.vector, contentDescription = null)
                                    }

                                    is Icon.SvgIcon -> {
                                        AsyncImage(
                                            model = icon.file,
                                            imageLoader = rememberSvgImageLoader(),
                                            contentDescription = null,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                if (currentTab == tab) {
                                    closeProjectDialog = true
                                } else {
                                    currentTab = tab
                                }
                            },
                            label = { Text(tab.getName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }

                    NavigationRailItem(
                        selected = false,
                        icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) },
                        onClick = { showAddDialog = true },
                        label = { Text(stringResource(strings.add)) },
                    )
                }

                VerticalDivider()

                Crossfade(targetState = currentTab, label = "file tree") { tab ->
                    if (tab != null) {
                        tab.Content(modifier = Modifier.weight(1f))
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().weight(1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                painter = painterResource(drawables.outline_folder),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(strings.no_folder_opened), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                if (showAddDialog) {
                    AddProjectDialog(
                        onDismiss = { showAddDialog = false },
                        openFolder = openFolder,
                        onAddProject = { fileObject -> scope.launch { addProject(fileObject, true) } },
                        showPrivateFileWarning = { callback ->
                            dialog(
                                title = strings.attention.getString(),
                                msg = strings.warning_private_dir.getString(),
                                onOk = { callback.invoke() },
                            )
                        },
                        showGitCloneDialog = {
                            showAddDialog = false
                            showGitCloneDialog = true
                        }
                    )
                }

                if (showGitCloneDialog) {
                    DoubleInputDialog(
                        title = stringResource(strings.clone_repo),
                        firstInputLabel = stringResource(strings.repo_url),
                        firstInputValue = repoURL,
                        onFirstInputValueChange = {
                            repoURL = it
                            repoURLError = validateValue(repoURL)
                        },
                        secondInputLabel = stringResource(strings.branch),
                        secondInputValue = repoBranch,
                        onSecondInputValueChange = {
                            repoBranch = it
                            repoBranchError = validateValue(repoBranch)
                        },
                        firstErrorMessage = repoURLError,
                        secondErrorMessage = repoBranchError,
                        onConfirm = {
                            // todo
                        },
                        onFinish = {
                            showGitCloneDialog = false
                            repoURL = ""
                            repoBranch = "main"
                            repoURLError = null
                            repoBranchError = null
                        },
                        confirmEnabled = repoURLError == null && repoBranchError == null && repoURL.isNotBlank()
                    )
                }

                if (closeProjectDialog && currentTab != null) {
                    CloseConfirmationDialog(
                        projectName = currentTab!!.getName(),
                        onConfirm = {
                            closeProjectDialog = false
                            removeProject(currentTab!!)
                        },
                        onDismiss = { closeProjectDialog = false },
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
    showPrivateFileWarning: (onOK: () -> Unit) -> Unit,
    showGitCloneDialog: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val lifecycleScope = remember { activity?.lifecycleScope ?: DefaultScope }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)) {
            AddDialogItem(
                icon = Icon.DrawableRes(drawables.file_symlink),
                title = stringResource(strings.open_directory),
                description = stringResource(strings.open_dir_desc),
                onClick = {
                    openFolder.launch(null)
                    onDismiss()
                },
            )

            // Open Path option
            val is11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            val isManager = is11Plus && Environment.isExternalStorageManager()
            val legacyPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED

            val storage = Environment.getExternalStorageDirectory()
            if (
                ((is11Plus && isManager) || (!is11Plus && legacyPermission)) && storage.canWrite() && storage.canRead()
            ) {
                AddDialogItem(
                    icon = Icon.DrawableRes(drawables.android),
                    title = stringResource(strings.open_path),
                    description = stringResource(strings.open_path_desc),
                    onClick = {
                        addProject(FileWrapper(storage))
                        onDismiss()
                    },
                )
            }

            if (InbuiltFeatures.debugMode.state.value) {
                AddDialogItem(
                    icon = Icon.DrawableRes(drawables.build),
                    title = stringResource(strings.private_files),
                    description = stringResource(strings.private_files_desc),
                    onClick = {
                        if (!Settings.has_shown_private_data_dir_warning) {
                            showPrivateFileWarning {
                                Settings.has_shown_private_data_dir_warning = true
                                lifecycleScope.launch { onAddProject(FileWrapper(activity!!.filesDir.parentFile!!)) }
                            }
                        } else {
                            lifecycleScope.launch { onAddProject(FileWrapper(activity!!.filesDir.parentFile!!)) }
                        }
                        onDismiss()
                    },
                )
            }

            // Clone repository option
            AddDialogItem(
                icon = Icon.DrawableRes(drawables.git),
                title = stringResource(strings.clone_repo),
                description = stringResource(strings.clone_repo_desc),
                onClick = {
                    showGitCloneDialog()
                    onDismiss()
                },
            )

            // Terminal Home option
            AddDialogItem(
                icon = Icon.DrawableRes(drawables.terminal),
                title = stringResource(strings.terminal_home),
                description = stringResource(strings.terminal_home_desc),
                onClick = {
                    if (!Settings.has_shown_terminal_dir_warning) {
                        showPrivateFileWarning {
                            Settings.has_shown_terminal_dir_warning = true
                            lifecycleScope.launch { onAddProject(FileWrapper(sandboxHomeDir())) }
                        }
                    } else {
                        lifecycleScope.launch { onAddProject(FileWrapper(sandboxHomeDir())) }
                    }
                    onDismiss()
                },
            )
        }
    }
}