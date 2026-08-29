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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rk.activities.main.MainActivity
import com.rk.file.toFileObject
import com.rk.filetree.ProjectCloseConfirmationDialog
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.dialogRes
import com.rk.utils.logError
import kotlinx.coroutines.launch

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

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val drawerTabs by viewModel.drawerTabs.collectAsStateWithLifecycle()
    val serviceTabs by viewModel.serviceTabs.collectAsStateWithLifecycle()
    val currentDrawerTabIndex by viewModel.currentDrawerTabIndex.collectAsStateWithLifecycle()
    val currentServiceTabIndex by viewModel.currentServiceTabIndex.collectAsStateWithLifecycle()
    val currentDrawerTab = drawerTabs.getOrNull(currentDrawerTabIndex)
    val currentServiceTab = serviceTabs.getOrNull(currentServiceTabIndex)

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
                        .onFailure { logError(it) }

                    scope.launch { viewModel.addFileTreeTab(it.toFileObject(expectedIsFile = false)) }
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

                val lazyListState = rememberLazyListState()
                val showHorizontalDivider by remember { derivedStateOf { lazyListState.canScrollForward } }

                NavigationRail(
                    modifier = Modifier.width(61.dp),
                    windowInsets = if (fullscreen) WindowInsets() else NavigationRailDefaults.windowInsets,
                ) {
                    Column(modifier = Modifier.fillMaxHeight()) {
                        LazyColumn(modifier = Modifier.weight(1f, fill = true), state = lazyListState) {
                            items(items = drawerTabs) { tab ->
                                if (!tab.isSupported()) return@items
                                NavigationRailItem(
                                    selected = currentDrawerTab == tab,
                                    icon = { XedIcon(tab.getIcon()) },
                                    onClick = {
                                        if (currentDrawerTab == tab && currentServiceTab == null) {
                                            closeProjectDialog = true
                                        } else {
                                            viewModel.selectDrawerTab(tab)
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
                    AddProjectSheet(
                        onDismiss = { showAddDialog = false },
                        openFolder = openFolder,
                        onAddProject = { fileObject -> scope.launch { viewModel.addFileTreeTab(fileObject, true) } },
                        showPrivateFileWarning = { callback ->
                            dialogRes(
                                title = strings.attention.getString(),
                                msg = strings.warning_private_dir.getString(),
                                onOk = { callback.invoke() },
                            )
                        },
                    )
                }

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
