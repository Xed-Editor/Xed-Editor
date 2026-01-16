package com.rk.filetree

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainActivity
import com.rk.activities.main.drawerStateRef
import com.rk.activities.main.fileTreeViewModel
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
import com.rk.resources.strings
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FileTreeTab(val root: FileObject) : DrawerTab() {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content(modifier: Modifier) {
        var fileActionDialog by remember { mutableStateOf<FileObject?>(null) }
        var searchDialog by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val mainViewModel = MainActivity.instance?.viewModel

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

        if (fileActionDialog != null && currentTab != null) {
            FileActionDialog(
                modifier = Modifier,
                file = fileActionDialog!!,
                root = root,
                onDismissRequest = { fileActionDialog = null },
                fileTreeViewModel = fileTreeViewModel.get()!!,
            )
        }

        if (searchDialog) {
            SearchDialog { searchDialog = false }
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun SearchDialog(onDismiss: () -> Unit) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                modifier =
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)
                        .verticalScroll(rememberScrollState())
            ) {
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
}
