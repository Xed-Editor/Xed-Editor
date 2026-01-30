package com.rk.filetree

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.components.compose.utils.addIf
import com.rk.components.getDrawerWidth
import com.rk.resources.drawables
import com.rk.settings.ReactiveSettings
import com.rk.utils.drawErrorUnderline
import com.rk.utils.getGitColor
import com.rk.utils.getUnderlineColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTreeNodeItem(
    modifier: Modifier,
    node: FileTreeNode,
    depth: Int,
    onFileClick: (FileTreeNode) -> Unit,
    onFileLongClick: (FileTreeNode) -> Unit,
    viewModel: FileTreeViewModel,
) {
    val isHidden = node.file.getName().startsWith(".")
    if (isHidden && !ReactiveSettings.showHiddenFilesDrawer) return

    val isExpanded = viewModel.isNodeExpanded(node.file)
    val horizontalPadding = (depth * 16).dp

    val isLoading = viewModel.isNodeLoading(node.file)
    val isCut = viewModel.isNodeCut(node.file)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Load children when expanded
    LaunchedEffect(node.file, isExpanded) {
        if (isExpanded && node.isDirectory) {
            viewModel.loadChildrenForNode(node)
        }
    }

    val children by
        remember(node.file, isExpanded) {
            derivedStateOf {
                if (node.isDirectory && isExpanded) {
                    viewModel.getNodeChildren(node)
                } else {
                    emptyList()
                }
            }
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.addIf(isCut) { alpha(0.5f) }
                    .combinedClickable(
                        onClick = {
                            if (node.isDirectory) {
                                viewModel.toggleNodeExpansion(node.file)
                            } else {
                                scope.launch {
                                    delay(100)
                                    onFileClick(node)
                                }
                            }
                            viewModel.selectedFile[(currentTab as FileTreeTab).root] = node.file
                        },
                        onLongClick = {
                            viewModel.selectedFile[(currentTab as FileTreeTab).root] = node.file
                            scope.launch {
                                delay(50)
                                onFileLongClick(node)
                            }
                        },
                    )
                    .then(
                        if (viewModel.selectedFile[(currentTab as? FileTreeTab)?.root] == node.file && !isCut) {
                            Modifier.background(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        } else {
                            Modifier
                        }
                    )
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(horizontalPadding))

            if (node.isDirectory) {
                val rotationDegree by
                    animateFloatAsState(targetValue = if (!isExpanded) 0f else 90f, label = "rotation")

                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(9.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            painter = painterResource(drawables.chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp).rotate(rotationDegree),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))
            } else {
                Spacer(modifier = Modifier.width(24.dp))
            }

            Box(modifier = Modifier.addIf(isHidden) { alpha(0.5f) }) { FileIcon(node.file, isExpanded = isExpanded) }

            Spacer(modifier = Modifier.width(8.dp))

            val underlineColor = getUnderlineColor(context, viewModel, node.file)
            Row(modifier = Modifier.width((getDrawerWidth() - 61.dp)), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.addIf(underlineColor != null) { drawErrorUnderline(underlineColor!!) },
                    color = getGitColor(viewModel, node.file) ?: MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        AnimatedVisibility(visible = isExpanded && node.isDirectory, enter = fadeIn(), exit = fadeOut()) {
            Column {
                children.forEach { childNode ->
                    key(childNode.file.hashCode()) {
                        FileTreeNodeItem(
                            modifier = Modifier.fillMaxWidth(),
                            node = childNode,
                            depth = depth + 1,
                            onFileClick = onFileClick,
                            onFileLongClick = onFileLongClick,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}
