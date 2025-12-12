package com.rk.filetree

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rk.activities.main.MainActivity
import com.rk.activities.main.drawerStateRef
import com.rk.activities.main.fileTreeViewModel
import com.rk.components.FileActionDialog
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.UriWrapper
import com.rk.file.sandboxHomeDir
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FileTreeTab(val root: FileObject) : DrawerTab() {
    @Composable
    override fun Content(modifier: Modifier) {
        var fileActionDialog by remember { mutableStateOf<FileObject?>(null) }
        val scope = rememberCoroutineScope()
        FileTree(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            rootNode = root.toFileTreeNode(),
            viewModel = fileTreeViewModel.get()!!,
            onFileClick = {
                if (it.isFile) {
                    scope.launch(Dispatchers.IO) {
                        if (it.file.isFile()) {
                            MainActivity.instance?.viewModel?.newTab(it.file, switchToTab = true)
                        }

                        delay(60)
                        if (Settings.keep_drawer_locked.not()) {
                            drawerStateRef.get()?.close()
                        }
                    }
                }
            },
            onFileLongClick = { fileActionDialog = it.file },
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
