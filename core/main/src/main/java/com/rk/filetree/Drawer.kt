package com.rk.filetree

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.fileTreeViewModel
import com.rk.activities.main.gitViewModel
import com.rk.components.AddDialogItem
import com.rk.components.DoubleInputDialog
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.external.SFTPConnection
import com.rk.file.external.SFTPFileObject
import com.rk.file.sandboxHomeDir
import com.rk.file.toFileObject
import com.rk.git.GitTab
import com.rk.git.ProgressCoordinator
import com.rk.icons.Icon
import com.rk.icons.XedIcon
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
import java.io.File
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext


object DrawerPersistence {
    private val saveMutex = Mutex()

    private const val DRAWER_TABS = "drawerTabs"
    private const val CURRENT_DRAWER_TAB = "currentDrawerTab"
    private const val EXPANDED_FILE_TREE_NODES = "expandedFileTree"

    suspend fun saveState() {
        saveMutex.withLock {
            val file = FileWrapper(application!!.filesDir.child(DRAWER_TABS))
            val serializableList = ArrayList(drawerTabs)
            file.writeObject(serializableList)

            val currentTabFile = FileWrapper(application!!.filesDir.child(CURRENT_DRAWER_TAB))
            if (currentDrawerTab != null) {
                currentTabFile.writeObject(currentDrawerTab!!)
            } else {
                currentTabFile.delete()
            }

            val expandedNodeFile = FileWrapper(application!!.filesDir.child(EXPANDED_FILE_TREE_NODES))
            fileTreeViewModel.get()?.getExpandedNodes()?.let { expandedNodeFile.writeObject(it) }
        }
    }

    suspend fun restoreState() {
        saveMutex.withLock {
            runCatching {
                    val loadedTabs =
                        withContext(Dispatchers.IO) {
                            val file = FileWrapper(application!!.filesDir.child(DRAWER_TABS))

                            if (file.exists() && file.canRead()) {
                                file.readObject() as? ArrayList<DrawerTab> ?: emptyList()
                            } else {
                                emptyList()
                            }
                        }

                    withContext(Dispatchers.IO) {
                        loadedTabs.forEach { tab ->
                            (tab as? FileTreeTab)?.root?.let { root ->
                                if (root is SFTPFileObject) {
                                    root.connect()
                                }
                            }
                        }
                    }

                    // Update the existing state list on Main thread
                    withContext(Dispatchers.Main) {
                        drawerTabs.clear()
                        drawerTabs.addAll(loadedTabs)
                    }

                    val currentTabFile = FileWrapper(application!!.filesDir.child(CURRENT_DRAWER_TAB))
                    if (currentTabFile.exists() && currentTabFile.canRead()) {
                        val currentTab = currentTabFile.readObject() as DrawerTab

                        if (currentTab is FileTreeTab && currentTab.root is SFTPFileObject) {
                            withContext(Dispatchers.IO) {
                                currentTab.root.connect()
                            }
                        }

                        selectTab(currentTab)
                    }

                    val expandedNodeFile = FileWrapper(application!!.filesDir.child(EXPANDED_FILE_TREE_NODES))
                    if (expandedNodeFile.exists() && expandedNodeFile.canRead()) {
                        fileTreeViewModel
                            .get()
                            ?.setExpandedNodes(expandedNodeFile.readObject() as Map<FileObject, Boolean>)
                    }
                }
                .onFailure {
                    it.printStackTrace()
                    toast(strings.project_restore_failed)
                }
        }
    }
}

suspend fun connectToSftpAndCreateFileObject(
    hostname: String,
    port: Int,
    username: String,
    password: String,
    initialPath: String?
): SFTPFileObject? {
    return withContext(Dispatchers.IO) {
        try {
            val connection = SFTPConnection(hostname, port, username, password)
            connection.connect()

            val actualInitialPath = try {
                val path = if (initialPath.isNullOrBlank()) "/" else initialPath
                connection.withSftpClient { sftp ->
                    sftp.canonicalize(path)
                }
            } catch (e: Exception) {
                Log.w(
                    "SFTP_CONNECT",
                    "Initial path '$initialPath' not found or not accessible, defaulting to PWD. Error: ${e.message}"
                )
                try {
                    connection.withSftpClient { it.sftpEngine.canonicalize(".") }
                } catch (e: Exception) {
                    "/"
                }
            }

            val rootAbsolutePath = "sftp://$username@$hostname:$port${
                if (actualInitialPath.startsWith("/")) actualInitialPath else "/$actualInitialPath"
            }"

            Log.i("SFTP_CONNECT", "Successfully connected to $hostname. Initial path: $actualInitialPath")
            toast("Successfully connected to $hostname. Initial path: $actualInitialPath")

            SFTPFileObject(connection, rootAbsolutePath, isRoot = true)

        } catch (e: Exception) {
            Log.e("SFTP_CONNECT", "SFTP connection to $hostname failed: ${e.message}", e)
            toast("SFTP connection to $hostname failed: ${e.message}")
            null
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SftpCredentialsDialog(
    onDismiss: () -> Unit,
    onSubmit: (hostname: String, port: Int, username: String, password: String, initialPath: String?) -> Unit
) {
    var hostname by rememberSaveable { mutableStateOf("") }
    var portText by rememberSaveable { mutableStateOf("22") } // Keep as text for TextField
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var initialPath by rememberSaveable { mutableStateOf("") }

        BasicAlertDialog(onDismissRequest = onDismiss) {


       Surface(modifier = Modifier.widthIn(min = 280.dp, max = 560.dp)) {
           Column(
               modifier = Modifier.padding(16.dp),
               verticalArrangement = Arrangement.spacedBy(8.dp),
               horizontalAlignment = Alignment.CenterHorizontally
           ) {
               Icon(
                   painter = painterResource(drawables.folder),
                   contentDescription = null,
                   Modifier.size(24.dp)
               )
               Spacer(modifier = Modifier.width(16.dp))

//            TODO: add Strings
               Text(
                   text = "Add SFTP Connection", // Title for the dialog
                   style = MaterialTheme.typography.titleLarge,
                   modifier = Modifier.padding(bottom = 8.dp)
               )

               OutlinedTextField(
                   value = hostname,
                   onValueChange = { hostname = it },
                   label = { Text("Hostname or IP Address") },
                   modifier = Modifier.fillMaxWidth(),
                   singleLine = true
               )

               OutlinedTextField(
                   value = portText,
                   onValueChange = { portText = it },
                   label = { Text("Port") },
                   keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                   modifier = Modifier.fillMaxWidth(),
                   singleLine = true
               )

               OutlinedTextField(
                   value = username,
                   onValueChange = { username = it },
                   label = { Text("Username") },
                   modifier = Modifier.fillMaxWidth(),
                   singleLine = true
               )

               OutlinedTextField(
                   value = password,
                   onValueChange = { password = it },
                   label = { Text("Password") },
                   visualTransformation = PasswordVisualTransformation(),
                   keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                   modifier = Modifier.fillMaxWidth(),
                   singleLine = true
               )

               OutlinedTextField(
                   value = initialPath,
                   onValueChange = { initialPath = it },
                   label = { Text("Initial Remote Path (Optional)") },
                   modifier = Modifier.fillMaxWidth(),
                   singleLine = true,
                   placeholder = { Text("/") }
               )

               Row(
                   modifier = Modifier.fillMaxWidth(),
                   horizontalArrangement = Arrangement.End,
                   verticalAlignment = Alignment.CenterVertically
               ) {
                   TextButton(onClick = onDismiss) {
                       Text("Cancel")
                   }
                   Spacer(modifier = Modifier.width(8.dp))
                   Button(
                       onClick = {
                           val port =
                               portText.toIntOrNull() ?: 22 // Default to 22 if input is invalid
                           val finalInitialPath = initialPath.ifBlank { null } // Use null if blank
                           onSubmit(hostname, port, username, password, finalInitialPath)
                           // onDismiss() will be called by the caller after onSubmit to close AddProjectDialog
                       }
                   ) {
                       Text("Connect")
                   }
               }
           }
       }
    }
}


fun createServices() {
    serviceTabs.clear()
    serviceTabs.add(GitTab(gitViewModel.get()!!))
}

var drawerTabs = mutableStateListOf<DrawerTab>()
var serviceTabs = mutableStateListOf<DrawerTab>()
var currentDrawerTab by mutableStateOf<DrawerTab?>(null)
var currentServiceTab by mutableStateOf<DrawerTab?>(null)

@OptIn(DelicateCoroutinesApi::class)
suspend fun addProject(fileObject: FileObject, save: Boolean = false) {
    val alreadyExistingProject = drawerTabs.find { it is FileTreeTab && it.root == fileObject }
    if (alreadyExistingProject != null) {
        selectTab(alreadyExistingProject)
        return
    }
    val tab = FileTreeTab(fileObject)
    tab.onAdded()
    drawerTabs.add(tab)
    selectTab(tab)
    if (save) {
        GlobalScope.launch(Dispatchers.IO) { DrawerPersistence.saveState() }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun addProject(tab: DrawerTab, save: Boolean = false) {
    tab.onAdded()
    drawerTabs.add(tab)
    selectTab(tab)
    if (save) {
        GlobalScope.launch(Dispatchers.IO) { DrawerPersistence.saveState() }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun removeProject(fileObject: FileObject, save: Boolean = false) {
    val index = drawerTabs.indexOfFirst { it is FileTreeTab && it.root == fileObject }
    if (index == -1) return

    if (currentDrawerTab == drawerTabs[index]) {
        val tabBefore = drawerTabs.getOrNull(index - 1)
        val tabAfter = drawerTabs.getOrNull(index + 1)
        selectTab(tabBefore ?: tabAfter)
    }

    drawerTabs[index].onRemoved()
    drawerTabs.removeAt(index)

    if (save) {
        GlobalScope.launch(Dispatchers.IO) { DrawerPersistence.saveState() }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun removeProject(tab: DrawerTab, save: Boolean = false) {
    val index = drawerTabs.indexOf(tab)
    if (index == -1) return

    if (currentDrawerTab == drawerTabs[index]) {
        val tabBefore = drawerTabs.getOrNull(index - 1)
        val tabAfter = drawerTabs.getOrNull(index + 1)
        selectTab(tabBefore ?: tabAfter)
    }

    drawerTabs[index].onRemoved()
    drawerTabs.removeAt(index)

    if (save) {
        GlobalScope.launch(Dispatchers.IO) { DrawerPersistence.saveState() }
    }
}

fun validateValue(value: String): String? {
    return when {
        value.isBlank() -> strings.value_empty_err.getString()
        else -> null
    }
}

fun selectTab(tab: DrawerTab?) {
    currentDrawerTab = tab
    currentServiceTab = null
}

var isLoading by mutableStateOf(true)

@Composable
fun DrawerContent(fullscreen: Boolean) {
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

                // Git clone dialog
                var showGitCloneDialog by remember { mutableStateOf(false) }

                var repoURL by remember { mutableStateOf("") }
                var repoBranch by remember { mutableStateOf("main") }

                var repoURLError by remember { mutableStateOf<String?>(null) }
                var repoBranchError by remember { mutableStateOf<String?>(null) }

                // Git clone progress dialog
                var showCloneProgressDialog by remember { mutableStateOf(false) }
                var progress by remember { mutableIntStateOf(0) }
                var maxProgress by remember { mutableIntStateOf(0) }
                var message by remember { mutableStateOf(strings.cloning.getString()) }

                val monitor = remember {
                    object : ProgressCoordinator {
                        private var cancelled = false

                        override fun start(totalTasks: Int) {}

                        override fun beginTask(title: String?, totalWork: Int) {
                            message = title ?: strings.cloning.getString()
                            maxProgress = totalWork
                            progress = 0
                        }

                        override fun update(completed: Int) {
                            progress += completed
                        }

                        override fun cancel() {
                            cancelled = true
                            hideDialog()
                        }

                        override fun endTask() {}

                        override fun isCancelled(): Boolean = cancelled || Thread.currentThread().isInterrupted

                        override fun showDialog() {
                            showCloneProgressDialog = true
                            progress = 0
                            maxProgress = 0
                            message = strings.cloning.getString()
                        }

                        override fun hideDialog() {
                            showCloneProgressDialog = false
                        }
                    }
                }

                val cloneGitRepo =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocumentTree(),
                        onResult = { uri ->
                            uri?.let {
                                runCatching {
                                        context.contentResolver.takePersistableUriPermission(
                                            it,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                        )
                                    }
                                    .onFailure { it.printStackTrace() }
                                scope.launch {
                                    val fileObject =
                                        it.toFileObject(expectedIsFile = false)
                                            .createChild(
                                                false,
                                                repoURL.substringAfterLast("/").substringBeforeLast("."),
                                            )
                                    gitViewModel
                                        .get()
                                        ?.cloneRepository(
                                            repoURL = repoURL,
                                            repoBranch = repoBranch,
                                            targetDir = File(fileObject!!.getAbsolutePath()),
                                            progressCoordinator = monitor,
                                            onComplete = { success ->
                                                repoURL = ""
                                                repoBranch = "main"
                                                repoURLError = null
                                                repoBranchError = null
                                                if (success) {
                                                    scope.launch { addProject(fileObject) }
                                                }
                                            },
                                        )
                                }
                            }
                        },
                    )

                val lazyListState = rememberLazyListState()
                val showHorizontalDivider by remember { derivedStateOf { lazyListState.canScrollForward } }

                NavigationRail(
                    modifier = Modifier.width(61.dp),
                    windowInsets = if (fullscreen) WindowInsets() else NavigationRailDefaults.windowInsets,
                ) {
                    Column(modifier = Modifier.fillMaxHeight()) {
                        LazyColumn(modifier = Modifier.weight(1f, fill = true), state = lazyListState) {
                            items(drawerTabs) { tab ->
                                if (!tab.isSupported()) return@items
                                NavigationRailItem(
                                    selected = currentDrawerTab == tab,
                                    icon = { XedIcon(tab.getIcon()) },
                                    onClick = {
                                        if (currentDrawerTab == tab && currentServiceTab == null) {
                                            closeProjectDialog = true
                                        } else {
                                            selectTab(tab)
                                        }
                                    },
                                    label = { Text(tab.getName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors =
                                        NavigationRailItemDefaults.colors().let {
                                            if (currentServiceTab == null) it
                                            else
                                                it.copy(
                                                    selectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    selectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    selectedIndicatorColor =
                                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                                )
                                        },
                                    enabled = tab.isEnabled(),
                                )
                            }

                            item {
                                NavigationRailItem(
                                    selected = false,
                                    icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) },
                                    onClick = { showAddDialog = true },
                                    label = { Text(stringResource(strings.add)) },
                                )
                            }
                        }

                        if (showHorizontalDivider) HorizontalDivider()

                        Column(modifier = Modifier.wrapContentHeight().padding(vertical = 8.dp)) {
                            serviceTabs.forEach { tab ->
                                if (!tab.isSupported()) return@forEach
                                NavigationRailItem(
                                    selected = currentServiceTab == tab,
                                    icon = { XedIcon(icon = tab.getIcon()) },
                                    onClick = {
                                        if (currentServiceTab == tab) {
                                            currentServiceTab = null
                                        } else {
                                            currentServiceTab = tab
                                        }
                                    },
                                    label = { Text(tab.getName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    enabled = tab.isEnabled(),
                                )
                            }
                        }
                    }
                }

                VerticalDivider()

                Surface {
                    Crossfade(targetState = currentDrawerTab, label = "file tree") { tab ->
                        if (currentServiceTab == null) {
                            if (tab != null) {
                                tab.Content(modifier = Modifier.fillMaxSize())
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        painter = painterResource(drawables.outline_folder),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        stringResource(strings.no_folder_opened),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    Crossfade(targetState = currentServiceTab) { tab ->
                        tab?.Content(modifier = Modifier.fillMaxSize())
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
                        },
                    )
                }

                if (showCloneProgressDialog) {
                    AlertDialog(
                        title = { Text(stringResource(strings.cloning)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = "$message ($progress/$maxProgress)",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                LinearProgressIndicator(
                                    progress = { if (maxProgress > 0) progress.toFloat() / maxProgress else 0f }
                                )
                            }
                        },
                        onDismissRequest = {},
                        confirmButton = {},
                        dismissButton = { TextButton({ monitor.cancel() }) { Text(stringResource(strings.cancel)) } },
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
                            showGitCloneDialog = false
                            cloneGitRepo.launch(null)
                        },
                        onDismiss = {
                            showGitCloneDialog = false
                            repoURL = ""
                            repoBranch = "main"
                            repoURLError = null
                            repoBranchError = null
                        },
                        confirmText = stringResource(strings.ok),
                        confirmEnabled = repoURLError == null && repoBranchError == null && repoURL.isNotBlank(),
                    )
                }

                if (closeProjectDialog && currentDrawerTab != null) {
                    ProjectCloseConfirmationDialog(
                        projectName = currentDrawerTab!!.getName(),
                        onConfirm = {
                            closeProjectDialog = false
                            removeProject(currentDrawerTab!!)
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
    showGitCloneDialog: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val lifecycleScope = remember { activity?.lifecycleScope ?: DefaultScope }

    // State Variables.
    var showSftpCredentialsDialog by rememberSaveable { mutableStateOf(false) }

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

//          todo: Icon for SFTP/Remote Folders.
            AddDialogItem(
                icon = drawables.folder,
                title = stringResource(strings.add_sftp),
                description = stringResource(strings.add_sftp_desc),
                onClick = {
                    // TODO: OnClick Handling.
                    showSftpCredentialsDialog = true;
//                    onDismiss()
                }
            )

            if(showSftpCredentialsDialog){
                SftpCredentialsDialog(
                    onDismiss = {
                        showSftpCredentialsDialog = false
                    },
                    onSubmit = { hostname, port, username, password, initialPath ->

                        showSftpCredentialsDialog = false

                        lifecycleScope.launch(Dispatchers.IO) {
                            val sftpFileObject = connectToSftpAndCreateFileObject(
                                hostname, port, username, password, initialPath
                            )

                            if (sftpFileObject != null && sftpFileObject is SFTPFileObject) {
                                sftpFileObject.prefetchAttributes()
//                                prefetching for the time being - just a workaround, hope it works
                                sftpFileObject.fetchChildren();

                                onAddProject(sftpFileObject) // This is the onAddProject from AddProjectDialog's parameters
                                onDismiss() // This is the onDismiss from AddProjectDialog's parameters, to close it.
                            } else {
                                // Handle connection failure, show error to user
                                // You might want to re-show the SftpCredentialsDialog or just show an error toast/dialog
                                // For now, just log. Consider showing a Toast or another XedDialog for the error.
                                Log.e("SFTP_CONNECT", "Failed to connect or create SFTP FileObject.")
                                // Optionally, re-show the AddProjectDialog or provide other feedback
                            }
                        }
                    })
            }

            // Open Path option
            val is11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            val isManager = is11Plus && Environment.isExternalStorageManager()
            val legacyPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED

            val storage = Environment.getExternalStorageDirectory()
            if ((isManager || (!is11Plus && legacyPermission)) && storage.canWrite() && storage.canRead()) {
                AddDialogItem(
                    icon = Icon.DrawableRes(drawables.android),
                    title = stringResource(strings.internal_storage),
                    description = stringResource(strings.open_internal_storage),
                    onClick = {
                        lifecycleScope.launch {
                            runCatching {
                                addProject(FileWrapper(storage))
                            }.onFailure { it.printStackTrace(); }
//                            Do nothing on success, As user sees the result :upside_down:
                        }
                        onDismiss()
                    },
                )
            }

            if (isManager) {
                val storageManager = context.getSystemService(StorageManager::class.java)
                val volumes = storageManager.storageVolumes

                volumes.forEach { volume ->
                    val root = volume.directory ?: return@forEach
                    if (root == storage) return@forEach
                    if (!root.canRead() || !root.canWrite() || root.listFiles() == null) return@forEach

                    val name = volume.getDescription(context)
                    val removable = volume.isRemovable
                    val description = if (removable) strings.open_removable_storage else strings.open_internal_storage

                    AddDialogItem(
                        icon = Icon.DrawableRes(drawables.sd_card),
                        title = name,
                        description = stringResource(description),
                    ) {
                        lifecycleScope.launch { addProject(FileWrapper(root)) }
                        onDismiss()
                    }
                }
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
            if (InbuiltFeatures.git.state.value) {
                AddDialogItem(
                    icon = Icon.DrawableRes(drawables.git),
                    title = stringResource(strings.clone_repo),
                    description = stringResource(strings.clone_repo_desc),
                    onClick = {
                        showGitCloneDialog()
                        onDismiss()
                    },
                )
            }

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
