package com.rk.filetree

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainActivity
import com.rk.activities.main.drawerStateRef
import com.rk.activities.main.fileTreeViewModel
import com.rk.activities.main.gitViewModel
import com.rk.activities.main.searchViewModel
import com.rk.components.AddDialogItem
import com.rk.components.FileActionDialog
import com.rk.components.codeSearchDialog
import com.rk.components.fileSearchDialog
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.UriWrapper
import com.rk.file.sandboxHomeDir
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.settings.app.InbuiltFeatures
import com.rk.utils.findGitRoot
import com.rk.utils.formatFileSize
import com.rk.utils.rememberNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FileTreeTab(val root: FileObject) : DrawerTab() {
    val indexingPreferenceKey
        get() = "enable_indexing_${root.hashCode()}"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content(modifier: Modifier) {
        var fileActionDialog by remember { mutableStateOf<FileObject?>(null) }
        var searchDialog by remember { mutableStateOf(false) }
        var enableIndexing by remember {
            mutableStateOf(Preference.getBoolean(indexingPreferenceKey, Settings.always_index_projects))
        }

        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val mainViewModel = MainActivity.instance?.viewModel

        LaunchedEffect(root) {
            if (InbuiltFeatures.git.state.value) {
                val gitRoot = findGitRoot(root.getAbsolutePath())
                if (gitRoot != null) {
                    gitViewModel.get()?.loadRepository(gitRoot)
                }
            }
        }

        LaunchedEffect(enableIndexing) {
            if (enableIndexing) {
                launch(Dispatchers.IO) { searchViewModel.get()?.index(context, root) }
            } else {
                launch(Dispatchers.IO) { searchViewModel.get()?.deleteIndex(context, root) }
            }
        }

        FileTree(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            rootNode = root.toFileTreeNode(),
            viewModel = fileTreeViewModel.get()!!,
            onFileClick = { node ->
                if (node.isFile) {
                    scope.launch(Dispatchers.IO) {
                        if (node.file.isFile()) {
                            mainViewModel?.newTab(node.file, switchToTab = true)
                        }

                        if (Settings.keep_drawer_locked.not()) {
                            drawerStateRef.get()?.close()
                        }
                    }
                }
            },
            onFileLongClick = { fileActionDialog = it.file },
            onSearchClick = { searchDialog = true },
        )

        if (fileActionDialog != null && currentDrawerTab != null) {
            FileActionDialog(
                file = fileActionDialog!!,
                root = root,
                onDismissRequest = { fileActionDialog = null },
                fileTreeViewModel = fileTreeViewModel.get()!!,
            )
        }

        if (searchDialog) {
            SearchDialog(
                { searchDialog = false },
                enableIndexing,
                { newValue ->
                    enableIndexing = newValue
                    Preference.setBoolean(indexingPreferenceKey, newValue)
                },
            )
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun SearchDialog(onDismiss: () -> Unit, enableIndexing: Boolean, toggleIndexing: (Boolean) -> Unit) {
        val context = LocalContext.current

        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                modifier =
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)
                        .verticalScroll(rememberScrollState())
            ) {
                val surfaceColor by
                    animateColorAsState(
                        if (enableIndexing) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = surfaceColor,
                    modifier = Modifier.clickable(onClick = { toggleIndexing(!enableIndexing) }),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        val contentColor by
                            animateColorAsState(
                                if (enableIndexing) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )

                        androidx.compose.material3.Icon(
                            painter = painterResource(drawables.bolt),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp),
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(strings.index_project),
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor,
                            )

                            val numberFormatter = rememberNumberFormatter()
                            var totalFiles by remember { mutableStateOf("0") }
                            var databaseSize by remember { mutableStateOf(formatFileSize(0)) }

                            val isIndexing by remember {
                                derivedStateOf { searchViewModel.get()?.isIndexing(root) == true }
                            }
                            LaunchedEffect(isIndexing) {
                                if (enableIndexing) {
                                    val stats = searchViewModel.get()?.getStats(context, root)
                                    totalFiles = numberFormatter.format(stats?.totalFiles ?: 0)
                                    databaseSize = formatFileSize(stats?.databaseSize ?: 0)
                                }
                            }

                            val desc =
                                when {
                                    !enableIndexing -> stringResource(strings.index_project_desc)
                                    isIndexing -> stringResource(strings.indexing)
                                    else -> {
                                        stringResource(strings.indexing_stats)
                                            .fillPlaceholders(totalFiles, databaseSize)
                                    }
                                }

                            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = contentColor)
                        }

                        Switch(
                            modifier = Modifier.height(24.dp),
                            checked = enableIndexing,
                            onCheckedChange = toggleIndexing,
                            colors =
                                SwitchDefaults.colors()
                                    .copy(
                                        uncheckedThumbColor = MaterialTheme.colorScheme.background,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                        uncheckedBorderColor = Color.Transparent,
                                    ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                AddDialogItem(
                    icon = Icons.Outlined.Search,
                    title = stringResource(strings.search_file_folder),
                    onClick = {
                        onDismiss()
                        fileSearchDialog = true
                    },
                )

                AddDialogItem(
                    icon = Icons.Outlined.Search,
                    title = stringResource(strings.search_code),
                    onClick = {
                        onDismiss()
                        codeSearchDialog = true
                    },
                )
            }
        }
    }

    override fun getName(): String {
        return root.getAppropriateName()
    }

    override fun getIcon(): Icon {
        val iconId =
            if ((root is UriWrapper && root.isTermuxUri()) || (root is FileWrapper && root.file == sandboxHomeDir())) {
                drawables.terminal
            } else {
                drawables.outline_folder
            }

        return Icon.DrawableRes(iconId)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is FileTreeTab) {
            return false
        }

        return other.root == root
    }

    override fun hashCode(): Int {
        return root.hashCode()
    }

    override fun isSupported(): Boolean {
        if (runBlocking { !root.exists() } || !root.isDirectory()) {
            removeProject(this@FileTreeTab, true)
            return false
        }
        return true
    }

    override fun onRemoved() {
        Preference.removeKey(indexingPreferenceKey)
        searchViewModel.get()?.deleteIndex(MainActivity.instance!!, root)
    }
}
