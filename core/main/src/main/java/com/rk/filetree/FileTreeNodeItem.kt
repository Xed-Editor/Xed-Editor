package com.rk.filetree

import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.rk.file.FileObject
import com.rk.resources.drawables
import com.rk.settings.Settings
import com.rk.utils.drawErrorUnderline
import com.rk.utils.getGitColor
import com.rk.utils.getUnderlineColor

@Composable
fun FileTreeNodeItem(
    modifier: Modifier,
    root: FileObject,
    node: FileTreeNode,
    depth: Int,
    onFileClick: (FileTreeNode) -> Unit,
    viewModel: FileTreeViewModel,
) {
    val isHidden = node.file.getName().startsWith(".")
    if (isHidden && !Settings.show_hidden_files_drawer) return

    val isExpanded = viewModel.isNodeExpanded(node.file)
    val horizontalPadding = (depth * 16).dp

    val isLoading = viewModel.isNodeLoading(node.file)
    val isCut = viewModel.isNodeCut(node.file)

    val isFileSelected = viewModel.isFileSelected(root, node.file)
    val isFileFocused = viewModel.isFileFocused(root, node.file)

    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val selectionColor = MaterialTheme.colorScheme.primaryContainer

    val nodeBackground = remember { Animatable(surfaceColor) }
    LaunchedEffect(isFileFocused, isFileSelected) {
        if (isFileFocused || isFileSelected) {
            nodeBackground.animateTo(selectionColor, animationSpec = tween(300))
        } else {
            nodeBackground.animateTo(surfaceColor, animationSpec = tween(300))
        }
    }

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

    var displayedChildren by remember { mutableStateOf(children) }
    var displayName by remember { mutableStateOf(node.name) }

    LaunchedEffect(children, Settings.compact_folders_drawer) {
        displayedChildren =
            if (Settings.compact_folders_drawer && children.size == 1 && children[0].isDirectory) {
                val collapsedNode = viewModel.collapseNode(node)
                viewModel.getNodeChildren(collapsedNode)
            } else children
        displayName =
            if (Settings.compact_folders_drawer) {
                viewModel.getCollapsedName(node)
            } else node.name
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.addIf(isCut) { alpha(0.5f) }
                    .background(nodeBackground.value)
                    .combinedClickable(
                        onClick = {
                            if (viewModel.isAnyFileSelected(root)) {
                                viewModel.toggleSelection(root, node.file)
                                return@combinedClickable
                            }

                            if (node.isDirectory) {
                                viewModel.toggleNodeExpansion(node.file)
                                return@combinedClickable
                            }

                            onFileClick(node)
                        },
                        onLongClick = { viewModel.toggleSelection(root, node.file) },
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
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.addIf(underlineColor != null) { drawErrorUnderline(underlineColor!!) },
                    color = getGitColor(node.file) ?: MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        AnimatedVisibility(
            modifier = Modifier.width(getDrawerWidth()),
            visible = isExpanded && node.isDirectory && children.isNotEmpty(),
        ) {
            Column {
                displayedChildren.forEach { childNode ->
                    key(childNode.file.hashCode(), childNode.name) {
                        FileTreeNodeItem(
                            modifier = Modifier.fillMaxWidth(),
                            root = root,
                            node = childNode,
                            depth = depth + 1,
                            onFileClick = onFileClick,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}
