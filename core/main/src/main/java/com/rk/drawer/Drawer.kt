package com.rk.drawer

import android.content.Intent
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.rk.activities.main.MainActivity
import com.rk.activities.main.gitViewModel
import com.rk.components.DoubleInputDialog
import com.rk.file.toFileObject
import com.rk.filetree.ProjectCloseConfirmationDialog
import com.rk.git.ProgressCoordinator
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.dialog
import kotlinx.coroutines.launch
import java.io.File

private fun validateValue(value: String): String? {
    return when {
        value.isBlank() -> strings.value_empty_err.getString()
        else -> null
    }
}

@Composable
fun DrawerContent(fullscreen: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val mainActivity = LocalActivity.current as MainActivity
    val viewModel = mainActivity.drawerViewModel

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

                    scope.launch { viewModel.addFileTreeTab(it.toFileObject(expectedIsFile = false)) }
                }
            },
        )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (viewModel.isLoading) {
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
                                                    viewModel.addFileTreeTab(fileObject)
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
                            items(items = viewModel.drawerTabs) { tab ->
                                if (!tab.isSupported()) return@items
                                NavigationRailItem(
                                    selected = viewModel.currentDrawerTab == tab,
                                    icon = { XedIcon(tab.getIcon()) },
                                    onClick = {
                                        if (viewModel.currentDrawerTab == tab && viewModel.currentServiceTab == null) {
                                            closeProjectDialog = true
                                        } else {
                                            viewModel.selectDrawerTab(tab)
                                        }
                                    },
                                    label = { Text(tab.getName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors =
                                        NavigationRailItemDefaults.colors().let {
                                            if (viewModel.currentServiceTab == null) it
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
                            viewModel.serviceTabs.forEach { tab ->
                                if (!tab.isSupported()) return@forEach
                                NavigationRailItem(
                                    selected = viewModel.currentServiceTab == tab,
                                    icon = { XedIcon(icon = tab.getIcon()) },
                                    onClick = {
                                        if (viewModel.currentServiceTab == tab) {
                                            viewModel.unselectServiceTab()
                                        } else {
                                            viewModel.selectServiceTab(tab)
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
                    Crossfade(targetState = viewModel.currentDrawerTab, label = "file tree") { tab ->
                        if (viewModel.currentServiceTab == null) {
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

                    Crossfade(targetState = viewModel.currentServiceTab) { tab ->
                        tab?.Content(modifier = Modifier.fillMaxSize())
                    }
                }

                if (showAddDialog) {
                    AddProjectSheet(
                        onDismiss = { showAddDialog = false },
                        openFolder = openFolder,
                        onAddProject = { fileObject -> scope.launch { viewModel.addFileTreeTab(fileObject, true) } },
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

                val currentDrawerTab = viewModel.currentDrawerTab
                if (closeProjectDialog && currentDrawerTab != null) {
                    ProjectCloseConfirmationDialog(
                        projectName = currentDrawerTab.getName(),
                        onConfirm = {
                            closeProjectDialog = false
                            viewModel.removeDrawerTab(currentDrawerTab)
                        },
                        onDismiss = { closeProjectDialog = false },
                    )
                }
            }
        }
    }
}
