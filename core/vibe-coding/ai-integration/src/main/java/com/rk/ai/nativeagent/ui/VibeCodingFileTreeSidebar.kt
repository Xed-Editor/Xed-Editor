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
import com.rk.theme.DesignTokens
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

    LaunchedEffect(workspacePath) {
        isLoading = true
        expandedPaths = emptySet()
        rootNodes = withContext(Dispatchers.IO) {
            try {
                val structure = ideService.getProjectStructure(workspacePath, 3, 200)
                parseStructure(structure)
            } catch (_: Exception) {
                emptyList()
            }
        }
        isLoading = false
    }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = colorScheme.surfaceContainerLow,
        tonalElevation = DesignTokens.Elevation.small,
    ) {
        Column(modifier = Modifier.padding(DesignTokens.Spacing.small)) {
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

            Spacer(Modifier.height(DesignTokens.Spacing.small))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search files...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(DesignTokens.Spacing.small))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            } else if (rootNodes.isEmpty()) {
                Text(
                    text = "No files found",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(DesignTokens.Spacing.small),
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
    val indent = DesignTokens.Spacing.medium.value * depth

    if (searchQuery.isNotBlank() && !matchesSearch(node, searchQuery)) return

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.isDirectory) onToggle(node.path)
                    else onOpen(node.path)
                }
                .padding(start = indent.dp, end = DesignTokens.Spacing.small, top = DesignTokens.Spacing.xsmall, bottom = DesignTokens.Spacing.xsmall),
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

private fun parseStructure(jsonString: String): List<FileNode> {
    // Simplified: parse from the getProjectStructure JSON format
    // Expected format: hierarchical tree with "name", "type" (file/dir), "children"
    return try {
        val json = com.google.gson.JsonParser.parseString(jsonString)
        parseJsonNode(json, "")
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseJsonNode(json: com.google.gson.JsonElement, parentPath: String): List<FileNode> {
    val nodes = mutableListOf<FileNode>()
    if (json.isJsonArray) {
        json.asJsonArray.forEach { element ->
            nodes.addAll(parseJsonNode(element, parentPath))
        }
    } else if (json.isJsonObject) {
        val obj = json.asJsonObject
        val name = obj["name"]?.asString ?: return nodes
        val type = obj["type"]?.asString ?: obj["kind"]?.asString ?: "file"
        val path = "$parentPath/$name"
        if (type == "directory" || type == "dir") {
            val children = if (obj.has("children")) {
                parseJsonNode(obj["children"], path)
            } else emptyList()
            nodes.add(FileNode(name, path, true, children))
        } else {
            nodes.add(FileNode(name, path, false))
        }
    }
    return nodes
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
