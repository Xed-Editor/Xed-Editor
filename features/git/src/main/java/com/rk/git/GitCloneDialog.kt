package com.rk.git

import android.content.Intent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.components.DoubleInputDialog
import com.rk.file.FileObject
import com.rk.file.toFileObject
import com.rk.resources.getString
import com.rk.resources.strings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun validateValue(value: String): String? {
    return when {
        value.isBlank() -> "Value cannot be empty"
        else -> null
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
                        gitViewModel.get()?.cloneRepository(
                            repoURL = repoURL,
                            repoBranch = repoBranch,
                            targetDir = File(fileObject!!.getAbsolutePath()),
                            progressCoordinator = monitor,
                            onComplete = { success ->
                                repoURL = ""
                                repoBranch = "main"
                                repoURLError = null
                                repoBranchError = null
                                onDismiss()
                                if (success) {
                                    onCloneComplete(fileObject)
                                }
                            },
                        )
                    }
                } ?: run {
                    onDismiss()
                }
            },
        )

    if (showCloneProgressDialog) {
        AlertDialog(
            title = { Text(stringResource(strings.cloning)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "$progressMessage ($progress/$maxProgress)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { if (maxProgress > 0) progress.toFloat() / maxProgress else 0f }
                    )
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
                cloneGitRepo.launch(null)
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
        )
    }
}
