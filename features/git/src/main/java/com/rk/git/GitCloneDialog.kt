package com.rk.git

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.components.DoubleInputDialog
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.sandboxHomeDir
import com.rk.file.toFileObject
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.launch
import java.io.File
import android.widget.Toast

private fun validateValue(value: String): String? {
    return when {
        value.isBlank() -> strings.value_empty_err.getString()
        else -> null
    }
}

private fun normalizeRepoUrl(url: String): String {
    val trimmed = url.trim()

    return if (
        trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            trimmed.startsWith("ssh://") ||
            trimmed.startsWith("git@")
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

@Composable
fun GitCloneDialog(
    onDismiss: () -> Unit,
    onCloneComplete: (FileObject) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var repoURL by remember { mutableStateOf("") }
    var repoBranch by remember { mutableStateOf("main") }
    var destinationFolder by remember { mutableStateOf<FileObject?>(null) }
    var destinationName by remember { mutableStateOf<String?>(null) }

    var repoURLError by remember { mutableStateOf<String?>(null) }
    var repoBranchError by remember { mutableStateOf<String?>(null) }

    var showCloneProgressDialog by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var maxProgress by remember { mutableIntStateOf(0) }
    var progressMessage by remember { mutableStateOf(strings.cloning.getString()) }

    val monitor = remember {
        object : ProgressCoordinator {
            private var cancelled = false

            override fun start(totalTasks: Int) {}

            override fun beginTask(title: String?, totalWork: Int) {
                progressMessage = title ?: strings.cloning.getString()
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
                progressMessage = strings.cloning.getString()
            }

            override fun hideDialog() {
                showCloneProgressDialog = false
            }
        }
    }

    fun cloneInto() {
        val destination = destinationFolder ?: FileWrapper(sandboxHomeDir())
        scope.launch {
            val repositoryName = repoURL.substringAfterLast("/").substringBeforeLast(".")
            val repositoryFolder = destination.createChild(false, repositoryName) ?: return@launch

            gitViewModel
                .get()
                ?.cloneRepository(
                    repoURL = normalizeRepoUrl(repoURL),
                    repoBranch = repoBranch,
                    targetDir = File(repositoryFolder.getAbsolutePath()),
                    progressCoordinator = monitor,
                    onComplete = { success ->
                        repoURL = ""
                        repoBranch = "main"
                        repoURLError = null
                        repoBranchError = null
                        onDismiss()
                        if (success) onCloneComplete(repositoryFolder)
                    },
                )
        }
    }

    val selectDestinationFolder =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { uri ->
                uri?.let {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            it,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }
                        .onFailure { it.printStackTrace() }

                    val folder = uri.toFileObject(expectedIsFile = false)
                    destinationFolder = folder
                    destinationName = folder.getName()
                } ?: run {
                    destinationFolder = null
                    destinationName = null
                }
            },
        )

    if (showCloneProgressDialog) {
        GitCloneProgressDialog(progressMessage, progress, maxProgress, monitor, onDismiss)
    } else {
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
                if (destinationFolder is FileWrapper) {
                    Toast.makeText(context, strings.unsupported_folder_for_feature, Toast.LENGTH_SHORT).show()
                } else {
                    cloneInto()
                }
            },
            onDismiss = {
                onDismiss()
                repoURL = ""
                repoBranch = "main"
                repoURLError = null
                repoBranchError = null
            },
            confirmText = stringResource(strings.ok),
            confirmEnabled = repoURLError == null && repoBranchError == null && repoURL.isNotBlank(),
            message = {
                val displayedDestination = destinationName ?: stringResource(strings.terminal_home)
                Text("${stringResource(strings.clone_destination)}: $displayedDestination")
                TextButton(onClick = { selectDestinationFolder.launch(null) }) {
                    Text(stringResource(strings.select_folder))
                }
            },
        )
    }
}

@Composable
private fun GitCloneProgressDialog(
    progressMessage: String,
    progress: Int,
    maxProgress: Int,
    monitor: ProgressCoordinator,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = { Text(stringResource(strings.cloning)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "$progressMessage ($progress/$maxProgress)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(progress = { if (maxProgress > 0) progress.toFloat() / maxProgress else 0f })
            }
        },
        onDismissRequest = {},
        confirmButton = {},
        dismissButton = {
            TextButton({
                monitor.cancel()
                onDismiss()
            }) {
                Text(stringResource(strings.cancel))
            }
        },
    )
}
