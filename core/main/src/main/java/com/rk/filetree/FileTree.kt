package com.rk.filetree

import android.os.Environment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainActivity
import com.rk.components.isPermanentDrawer
import com.rk.file.FileObject
import com.rk.resources.getString
import com.rk.resources.strings

@Composable
fun FileTree(
    rootNode: FileTreeNode,
    modifier: Modifier = Modifier,
    onFileClick: FileTreeNode.(FileTreeNode) -> Unit,
    onFileLongClick: FileTreeNode.(FileTreeNode) -> Unit = {},
    viewModel: FileTreeViewModel
) {

    // Auto-expand root node on first composition
    LaunchedEffect(rootNode.file) {
        if (!viewModel.isNodeExpanded(rootNode.file)) {
            viewModel.toggleNodeExpansion(rootNode.file)
            viewModel.loadChildrenForNode(rootNode)
        }
    }

    LaunchedEffect(MainActivity.instance) {
        MainActivity.instance?.foregroundListener["fileTreeRefresh"] = { resumed ->
            if (resumed) {
                viewModel.refreshEverything()
            }
        }
    }

    Surface(
        modifier = modifier,
        color = if (isPermanentDrawer) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = rootNode.file.hashCode()) {
                    FileTreeNodeItem(
                        modifier = Modifier.fillMaxWidth(),
                        node = rootNode,
                        depth = 0,
                        onFileClick = {
                            rootNode.onFileClick(it)
                        },
                        onFileLongClick = {
                            rootNode.onFileLongClick(it)
                        },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

fun FileObject.getAppropriateName(): String {
    return if (getAbsolutePath() == Environment.getExternalStorageDirectory().absolutePath) {
        strings.storage.getString()
    } else {
        getName()
    }
}