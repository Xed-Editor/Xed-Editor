@file:OptIn(ExperimentalUuidApi::class)

package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.service.IdeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

private data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileNode> = emptyList(),
)

@Composable
fun VibeCodingFileTreeSidebar(
    ideService: IdeService,
    workspacePath: String,
    onOpenFile: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    var rootNodes by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    // Check if workspace is configured
    val hasWorkspace = remember(workspacePath) {
        workspacePath.isNotBlank() && runCatching { java.io.File(workspacePath).exists() }.getOrDefault(false)
    }

    LaunchedEffect(workspacePath, hasWorkspace) {
        isLoading = true
        expandedPaths = emptySet()
        rootNodes = if (hasWorkspace) {
            withContext(Dispatchers.IO) {
                try {
                    val structure = ideService.getProjectStructure(workspacePath, 3, 200)
                    parseStructure(structure)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        } else emptyList()
        isLoading = false
    }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Files",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                FilledTonalIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search files...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            } else if (rootNodes.isEmpty()) {
                Text(
                    text = if (hasWorkspace) "No files found" else "Open a project from the file tree first",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(8.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                ) {
                    val filtered = if (searchQuery.isBlank()) rootNodes
                    else filterNodes(rootNodes, searchQuery)

                    items(filtered, key = { it.path }) { node ->
                        FileTreeItem(
                            node = node,
                            depth = 0,
                            isExpanded = node.path in expandedPaths,
                            searchQuery = searchQuery,
                            expandedPaths = expandedPaths,
                            onToggle = {
                                expandedPaths = if (it in expandedPaths) {
                                    expandedPaths - it
                                } else {
                                    expandedPaths + it
                                }
                            },
                            onOpen = { onOpenFile(it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileTreeItem(
    node: FileNode,
    depth: Int,
    isExpanded: Boolean,
    searchQuery: String,
    expandedPaths: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val indent = 12f * depth

    if (searchQuery.isNotBlank() && !matchesSearch(node, searchQuery)) return

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.isDirectory) onToggle(node.path)
                    else onOpen(node.path)
                }
                .padding(start = indent.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (node.isDirectory) {
                Text(
                    text = if (isExpanded) "▾" else "▸",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.primary,
                    modifier = Modifier.width(12.dp),
                )
            } else {
                Spacer(Modifier.width(12.dp))
            }

            Spacer(Modifier.width(4.dp))

            val icon: ImageVector = when {
                node.isDirectory -> if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder
                node.name.endsWith(".kt") || node.name.endsWith(".kts") -> Icons.Outlined.Code
                node.name.endsWith(".java") -> Icons.Outlined.Code
                node.name.endsWith(".xml") || node.name.endsWith(".html") -> Icons.Outlined.Code
                node.name.endsWith(".json") -> Icons.Outlined.Settings
                node.name.endsWith(".gradle") -> Icons.Outlined.Settings
                node.name.endsWith(".md") -> Icons.Outlined.Description
                node.name.endsWith(".png") || node.name.endsWith(".jpg") -> Icons.Outlined.Image
                else -> Icons.Outlined.Description
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (node.isDirectory) FontWeight.Medium else FontWeight.Normal,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorScheme.onSurface,
            )
        }

        AnimatedVisibility(
            visible = isExpanded && node.isDirectory,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                node.children.forEach { child ->
                    FileTreeItem(
                        node = child,
                        depth = depth + 1,
                        isExpanded = child.path in expandedPaths,
                        searchQuery = searchQuery,
                        expandedPaths = expandedPaths,
                        onToggle = onToggle,
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

private fun parseStructure(treeText: String): List<FileNode> {
    // Parse text tree format from ProjectService.getProjectStructure:
    //   [D] project/
    //     [F] build.gradle.kts
    //     [D] src/
    //       [F] MainActivity.kt
    return try {
        val lines = treeText.lines().filter { it.isNotBlank() }
        val rootNodes = mutableListOf<FileNode>()
        val stack = mutableListOf<Pair<Int, MutableList<FileNode>>>()
        val parentPathStack = mutableListOf<String>()

        for (line in lines) {
            val stripped = line.trimStart()
            val indent = line.length - stripped.length
            val depth = indent / 2
            val isDir = stripped.startsWith("[D]")
            val isFile = stripped.startsWith("[F]")
            if (!isDir && !isFile) continue
            val name = stripped.removePrefix("[D] ").removePrefix("[F] ").trimEnd('/')

            while (stack.isNotEmpty() && stack.last().first >= depth) {
                stack.removeAt(stack.lastIndex)
                if (parentPathStack.size > depth - 1) {
                    parentPathStack.removeAt(parentPathStack.lastIndex)
                }
            }

            val parentPrefix = if (parentPathStack.isNotEmpty()) parentPathStack.joinToString("/") + "/" else ""
            val path = parentPrefix + name

            if (isDir) {
                val children = mutableListOf<FileNode>()
                val node = FileNode(name, path, true, children)
                if (stack.isNotEmpty()) {
                    stack.last().second.add(node)
                } else {
                    rootNodes.add(node)
                }
                stack.add(depth to children)
                if (parentPathStack.size <= depth - 1) {
                    while (parentPathStack.size < depth) {
                        parentPathStack.add("")
                    }
                }
                if (parentPathStack.size > depth) {
                    parentPathStack[depth] = name
                } else {
                    parentPathStack.add(name)
                }
            } else {
                val node = FileNode(name, path, false)
                if (stack.isNotEmpty()) {
                    stack.last().second.add(node)
                } else {
                    rootNodes.add(node)
                }
            }
        }

        if (rootNodes.size == 1 && rootNodes[0].isDirectory) {
            rootNodes[0].children
        } else {
            rootNodes
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun filterNodes(nodes: List<FileNode>, query: String): List<FileNode> {
    return nodes.mapNotNull { node ->
        if (node.name.contains(query, ignoreCase = true)) {
            if (node.isDirectory) node.copy(children = filterNodes(node.children, query))
            else node
        } else if (node.isDirectory) {
            val filtered = filterNodes(node.children, query)
            if (filtered.isNotEmpty()) node.copy(children = filtered)
            else null
        } else null
    }
}

private fun matchesSearch(node: FileNode, query: String): Boolean {
    if (node.name.contains(query, ignoreCase = true)) return true
    if (node.isDirectory) return node.children.any { matchesSearch(it, query) }
    return false
}
