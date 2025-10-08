package com.rk.compose.filetree

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.UriWrapper
import com.rk.DefaultScope
import com.rk.components.compose.preferences.base.DividerColumn
import com.rk.file.child
import com.rk.file.external.SFTPFileObject
import com.rk.file.sandboxHomeDir
import com.rk.file.toFileObject
import com.rk.libcommons.application
import com.rk.libcommons.dialog
import com.rk.libcommons.errorDialog
import com.rk.libcommons.toast
import com.rk.resources.drawables
import com.rk.resources.fillPlaceholders
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.ui.activities.main.MainActivity
import com.rk.xededitor.ui.components.FileActionDialog
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Serializable
import com.rk.xededitor.ui.components.*
import kotlinx.coroutines.delay
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import kotlin.onFailure


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
                        list.addAll((ois.readObject() as List<FileObject>).map { 
                            if(it is SFTPFileObject){
                                it.connect()
                            }
                            FileObjectWrapper(it, it.getName())
                        })
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

suspend fun connectToSftpAndCreateFileObject(
    hostname: String,
    port: Int,
    username: String,
    password: String,
    initialPath: String?
): SFTPFileObject? {
    return withContext(Dispatchers.IO) {
        var session: Session? = null
        var channelSftp: ChannelSftp? = null

        try {
            session = SFTPFileObject.createSessionInternal(hostname, port, username, password)
            if (session == null || !session.isConnected) {
                toast("Failed to create or connect SFTP channel to $hostname")
                Log.e("SFTP_CONNECT", "Failed to create or connect session to $hostname")
                return@withContext null
            }

            channelSftp = SFTPFileObject.createChannelInternal(session)
            if (channelSftp == null || !channelSftp.isConnected) {
                toast("Failed to create or connect SFTP channel to $hostname")
                Log.e("SFTP_CONNECT", "Failed to create or connect SFTP channel to $hostname")
                session.disconnect() // Clean up session
                return@withContext null
            }

            // Determine the actual initial path on the server
            val actualInitialPath = run {
                // Ensure initialPathString starts with a slash if it's not empty and not already the case
                val path = if (initialPath.isNullOrBlank()) "/" else initialPath
                // You might want to verify if this path exists or cd to it
                try {
                    channelSftp.cd(path) // Try to change to the path
                    channelSftp.pwd()    // Get the canonical path after cd
                } catch (e: SftpException) {
                    Log.w("SFTP_CONNECT", "Initial path '$path' not found or not accessible, defaulting to PWD. Error: ${e.message}")
                    channelSftp.pwd() // Default to current working directory on error
                }
            }


            val rootAbsolutePath = "sftp://$username@$hostname:$port${if (actualInitialPath.startsWith("/")) actualInitialPath else "/$actualInitialPath"}"

            Log.i("SFTP_CONNECT", "Successfully connected to $hostname. Initial path: $actualInitialPath")

            toast("Successfully connected to $hostname. Initial path: $actualInitialPath")

            // Create the root FileObject for this SFTP connection
            // The sftpFileObject will hold the session and channel.
            SFTPFileObject(hostname, port, username, password,
                session, channelSftp, rootAbsolutePath, isRoot = true)

        } catch (e: Exception) { // Catch JSchException, SftpException, etc.
            Log.e("SFTP_CONNECT", "SFTP connection to $hostname failed: ${e.message}", e)
            toast("SFTP connection to $hostname failed: ${e.message}")
            channelSftp?.disconnect()
            session?.disconnect()
            null
        }

    }
}

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

        XedDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                        val port = portText.toIntOrNull() ?: 22 // Default to 22 if input is invalid
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


var projects = mutableStateListOf<FileObjectWrapper>()
var currentProject by mutableStateOf<FileObject?>(null)

@OptIn(DelicateCoroutinesApi::class)
suspend fun addProject(fileObject: FileObject, save: Boolean = false) {
    if (projects.find { it.fileObject == fileObject } != null) {
        return
    }

    val projectName = withContext(Dispatchers.IO) {
        fileObject.getName()
    }

    withContext(Dispatchers.Main) {
        projects.add(FileObjectWrapper(fileObject = fileObject, name = projectName))
        currentProject = fileObject
    }

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
fun DrawerContent(modifier: Modifier = Modifier,onFileSelected:(FileObject)-> Unit) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()

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

                scope.launch {
                    runCatching {
                        addProject(it.toFileObject(isFile = false))
                    }.onFailure { it.printStackTrace();errorDialog(it) }
//                    Do nothing on success, As user sees the result :upside_down:

                }
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
                var closeProjectDialog by remember { mutableStateOf<FileObject?>(null) }

                NavigationRail(modifier = Modifier.width(61.dp)) {
                    projects.forEach { file ->
                        NavigationRailItem(
                            selected = file.fileObject == currentProject,
                            icon = {
                                val iconId =
                                    if ((file.fileObject is UriWrapper && file.fileObject.isTermuxUri()) || (file.fileObject is FileWrapper && file.fileObject.file == sandboxHomeDir())) {
                                        drawables.terminal
                                    } else {
                                        drawables.outline_folder_24
                                    }
                                Icon(painter = painterResource(iconId), contentDescription = null)
                            },
                            onClick = {
                                if (file.fileObject == currentProject) {
                                    closeProjectDialog = file.fileObject
                                } else {
                                    scope.launch {
                                        currentProject = file.fileObject
                                    }
                                }

                            },
                            label = {

                                val fileName by produceState<String>(
                                    initialValue = stringResource(strings.unknown),
                                    key1 = file
                                ) {
                                    value = file.fileObject.getAppropriateName()
                                }

                                Text(
                                    fileName,
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
                            root = project,
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
                                painter = painterResource(drawables.outline_folder_24),
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
                    })
                }

                if (closeProjectDialog != null){
                    AlertDialog(
                        onDismissRequest = {
                            closeProjectDialog = null
                        },
                        title = {
                            Text(text = strings.close.getString())
                        },
                        text = {
                            val projectName by produceState<String?>(
                                initialValue = stringResource(strings.unknown),
                                key1 = closeProjectDialog
                            ) {
                                value = closeProjectDialog?.getName()
                            }

                            Text(
                                text = stringResource(strings.close_current_project).fillPlaceholders(
                                    mapOf(
                                        "project_name" to (projectName ?: stringResource(
                                            strings.unknown
                                        ))
                                    )
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                closeProjectDialog = null
                                currentProject?.let { removeProject(it) }
                            }) {
                                Text(text = strings.close.getString())
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                closeProjectDialog = null
                            }) {
                                Text(text = strings.cancel.getString())
                            }
                        }
                    )
                }

            }
        }
    }
}

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

    // State Variables.
    var showSftpCredentialsDialog by rememberSaveable { mutableStateOf(false) }

    XedDialog(onDismissRequest = onDismiss) {
        DividerColumn {
            AddDialogItem(
                icon = drawables.file_symlink,
                title = stringResource(strings.open_directory),
                description = stringResource(strings.open_dir_desc),
                onClick = {
                    openFolder.launch(null)
                    onDismiss()
                }
            )


            AddDialogItem(
                icon = drawables.upload,
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
                        lifecycleScope.launch {
                            runCatching {
                                addProject(FileWrapper(storage))
                            }.onFailure { it.printStackTrace();errorDialog(it) }
//                            Do nothing on success, As user sees the result :upside_down:
                        }
                        onDismiss()
                    }
                )
            }

            // Private Files option (debug only)
            if (BuildConfig.DEBUG) {
                AddDialogItem(
                    icon = drawables.build,
                    title = stringResource(strings.private_files),
                    description = stringResource(strings.private_files_desc),
                    onClick = {
                        if (!Settings.has_shown_private_data_dir_warning) {
                            showPrivateFileWarning{
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
                        showPrivateFileWarning{
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





