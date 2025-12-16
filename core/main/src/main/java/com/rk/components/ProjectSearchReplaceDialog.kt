package com.rk.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.components.compose.utils.addIf
import com.rk.file.FileObject
import com.rk.filetree.FileIcon
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.searchreplace.ProjectReplaceManager
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data class for search match result
data class SearchMatch(
    val lineNumber: Int,
    val columnStart: Int,
    val columnEnd: Int,
    val lineContent: String,
    val snippet: AnnotatedString?,
)

// Local helper function to check for binary characters
private fun checkBinaryChars(fileContent: String): Boolean {
    val checkedCharacters = 1024
    val threshold = 0.3

    val checkText = fileContent.take(checkedCharacters)
    val total = checkText.length
    if (total == 0) return false

    val binarySymbolsCount =
        checkText.count { c ->
            (c.code < 32 && c.code != 9 && c.code != 10 && c.code != 12 && c.code != 13) || c.code > 126
        }

    return binarySymbolsCount.toDouble() / total > threshold
}

private suspend fun searchInProject(
    mainViewModel: MainViewModel,
    scope: CoroutineScope,
    parent: FileObject,
    query: String,
    options: ProjectReplaceManager.SearchOptions,
    results: SnapshotStateMap<FileObject, MutableList<SearchMatch>>,
) {
    val childFiles = parent.listFiles()
    val context = currentCoroutineContext()
    val openedEditorTabs = mainViewModel.tabs.mapNotNull { it as? EditorTab }
    if (!context.isActive) return

    // Build regex from options
    val searchRegex = runCatching { 
        ProjectReplaceManager.buildSearchRegex(query, options) 
    }.getOrNull() ?: return

    for (file in childFiles) {
        if (!context.isActive) return

        val isHidden = file.getName().startsWith(".")
        if (isHidden && !Settings.show_hidden_files_search) continue

        if (file.isDirectory()) {
            searchInProject(mainViewModel, scope, file, query, options, results)
            continue
        }

        if (file.length() > 10_000_000) continue // Skip files over 10MB
        val ext = file.getName().substringAfterLast(".", "")
        if (ext.lowercase() in binaryExtensions) continue

        val openedTab = openedEditorTabs.find { it.file == file }
        val lines =
            if (openedTab != null) {
                openedTab.editorState.editor.get()?.text.toString().lines()
            } else {
                val fileText = file.readText()
                if (checkBinaryChars(fileText ?: "")) continue
                fileText?.lines() ?: emptyList()
            }

        val fileMatches = mutableListOf<SearchMatch>()

        lines.forEachIndexed { lineIndex, line ->
            val matchResult = searchRegex.find(line)
            if (matchResult != null) {
                val charIndex = matchResult.range.first
                val matchLength = matchResult.value.length
                val fileExt = file.getName().substringAfterLast(".")

                val snippet = runCatching {
                    generateSnippet(
                        context = MainActivity.instance!!,
                        targetLine = line,
                        fileExt = fileExt,
                        start = charIndex,
                        end = charIndex + matchLength,
                    )
                }.getOrNull()

                fileMatches.add(
                    SearchMatch(
                        lineNumber = lineIndex + 1,
                        columnStart = charIndex + 1,
                        columnEnd = charIndex + matchLength + 1,
                        lineContent = line,
                        snippet = snippet,
                    )
                )
            }
        }

        if (fileMatches.isNotEmpty()) {
            results[file] = fileMatches
        }
    }
}

@Composable
fun ProjectSearchReplaceDialog(
    viewModel: MainViewModel,
    projectFile: FileObject,
    onFinish: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showReplace by remember { mutableStateOf(true) }
    // Search options
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val searchResults = remember { mutableStateMapOf<FileObject, MutableList<SearchMatch>>() }
    var isSearching by remember { mutableStateOf(false) }
    var isReplacing by remember { mutableStateOf(false) }
    val expandedFiles = remember { mutableStateListOf<FileObject>() }

    // Debounced search - re-trigger when query or options change
    LaunchedEffect(searchQuery, caseSensitive, wholeWord, useRegex) {
        if (searchQuery.isEmpty()) {
            searchResults.clear()
            return@LaunchedEffect
        }

        delay(300)
        isSearching = true
        searchResults.clear()
        val options = ProjectReplaceManager.SearchOptions(caseSensitive, wholeWord, useRegex)
        searchInProject(viewModel, scope, projectFile, searchQuery, options, searchResults)
        isSearching = false
        listState.scrollToItem(0)
    }

    val totalMatches by remember { derivedStateOf { searchResults.values.sumOf { it.size } } }
    val totalFiles by remember { derivedStateOf { searchResults.size } }

    XedDialog(onDismissRequest = onFinish, modifier = Modifier.imePadding()) {
        Column(modifier = Modifier.animateContentSize().padding(8.dp)) {
            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                maxLines = 1,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                label = { Text("Find") },
                placeholder = { Text(text = stringResource(strings.enter_code_snippet)) },
                trailingIcon = {
                    IconButton(onClick = { showReplace = !showReplace }) {
                        Icon(
                            painter = painterResource(drawables.find_replace),
                            contentDescription = "Toggle Replace",
                            tint = if (showReplace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            )

            // Search options row
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Case Sensitive toggle
                OutlinedButton(
                    onClick = { caseSensitive = !caseSensitive },
                    modifier = Modifier.height(32.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                    colors = if (caseSensitive) ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("Aa", fontSize = 12.sp)
                }
                
                // Whole Word toggle
                OutlinedButton(
                    onClick = { wholeWord = !wholeWord },
                    modifier = Modifier.height(32.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                    colors = if (wholeWord) ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("[W]", fontSize = 12.sp)
                }
                
                // Regex toggle
                OutlinedButton(
                    onClick = { useRegex = !useRegex },
                    modifier = Modifier.height(32.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                    colors = if (useRegex) ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(".*", fontSize = 12.sp)
                }
            }

            // Replace input (collapsible)
            AnimatedVisibility(
                visible = showReplace,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = { replaceQuery = it },
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Replace with") },
                    placeholder = { Text("Leave empty to delete matches") },
                )
            }

            // Results summary and actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isSearching || isReplacing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        text = when {
                            searchQuery.isEmpty() -> stringResource(strings.empty_code_results)
                            totalMatches > 0 -> "$totalMatches matches in $totalFiles files"
                            else -> stringResource(strings.no_results)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }

                // Replace All button
                if (showReplace && searchQuery.isNotEmpty() && totalMatches > 0) {
                    Button(
                        onClick = {
                            if (searchQuery.isBlank()) {
                                toast("Find text can't be empty")
                                return@Button
                            }
                            isReplacing = true
                            val options = ProjectReplaceManager.SearchOptions(caseSensitive, wholeWord, useRegex)
                            scope.launch(Dispatchers.IO) {
                                ProjectReplaceManager.replaceAllInProject(
                                    projectRoot = projectFile,
                                    query = searchQuery,
                                    replacement = replaceQuery,
                                    options = options,
                                )
                                // Re-search after replace
                                searchResults.clear()
                                searchInProject(viewModel, scope, projectFile, searchQuery, options, searchResults)
                                isReplacing = false
                            }
                        },
                        enabled = !isReplacing,
                        modifier = Modifier.height(32.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text("Replace All", fontSize = 12.sp)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            // File list with matches
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                searchResults.forEach { (fileObject, matches) ->
                    val isHidden = fileObject.getName().startsWith(".") || fileObject.getAbsolutePath().contains("/.")
                    val isExpanded = fileObject in expandedFiles

                    // File header
                    item(key = "header_${fileObject.getAbsolutePath()}") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isExpanded) expandedFiles.remove(fileObject)
                                    else expandedFiles.add(fileObject)
                                }
                                .addIf(isHidden) { Modifier.alpha(0.5f) }
                                .padding(vertical = 8.dp),
                        ) {
                            Icon(
                                painter = painterResource(if (isExpanded) drawables.arrow_upward else drawables.arrow_downward),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            FileIcon(file = fileObject, iconTint = MaterialTheme.colorScheme.primary)

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fileObject.getName(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${matches.size} match${if (matches.size > 1) "es" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }

                            // Replace in this file button
                            if (showReplace) {
                                OutlinedButton(
                                    onClick = {
                                        val options = ProjectReplaceManager.SearchOptions(caseSensitive, wholeWord, useRegex)
                                        scope.launch(Dispatchers.IO) {
                                            val content = fileObject.readText() ?: return@launch
                                            val regex = runCatching { 
                                                ProjectReplaceManager.buildSearchRegex(searchQuery, options) 
                                            }.getOrElse { 
                                                toast("Invalid regex pattern")
                                                return@launch 
                                            }
                                            val escapedReplace = ProjectReplaceManager.escapeReplacement(replaceQuery, options.useRegex)
                                            val newContent = content.replace(regex, escapedReplace)
                                            if (newContent != content) {
                                                fileObject.writeText(newContent)
                                                toast("Replaced in ${fileObject.getName()}")
                                                // Update search results
                                                withContext(Dispatchers.Main) {
                                                    searchResults.remove(fileObject)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = ButtonDefaults.ContentPadding,
                                ) {
                                    Text("Replace", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Expanded matches
                    if (isExpanded) {
                        items(
                            items = matches,
                            key = { match -> "${fileObject.getAbsolutePath()}_${match.lineNumber}_${match.columnStart}" }
                        ) { match ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Navigate to file and line
                                        DefaultScope.launch {
                                            viewModel.newTab(fileObject, checkDuplicate = true, switchToTab = true)
                                            // TODO: Navigate to specific line after tab opens
                                        }
                                        onFinish()
                                    }
                                    .padding(start = 32.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
                                    .addIf(isHidden) { Modifier.alpha(0.5f) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${match.lineNumber}:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.width(40.dp),
                                )

                                if (match.snippet != null) {
                                    Text(
                                        text = match.snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Text(
                                        text = match.lineContent.trim(),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // Bottom actions with Undo/Redo
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Undo button
                val canUndoState by ProjectReplaceManager.canUndo
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            ProjectReplaceManager.undoLastReplace()
                            // Refresh search results
                            val options = ProjectReplaceManager.SearchOptions(caseSensitive, wholeWord, useRegex)
                            searchResults.clear()
                            if (searchQuery.isNotEmpty()) {
                                searchInProject(viewModel, scope, projectFile, searchQuery, options, searchResults)
                            }
                        }
                    },
                    enabled = canUndoState,
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(
                        painter = painterResource(drawables.undo),
                        contentDescription = "Undo",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Undo", fontSize = 12.sp)
                }

                // Redo button
                val canRedoState by ProjectReplaceManager.canRedo
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            ProjectReplaceManager.redoLastReplace()
                            // Refresh search results
                            val options = ProjectReplaceManager.SearchOptions(caseSensitive, wholeWord, useRegex)
                            searchResults.clear()
                            if (searchQuery.isNotEmpty()) {
                                searchInProject(viewModel, scope, projectFile, searchQuery, options, searchResults)
                            }
                        }
                    },
                    enabled = canRedoState,
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(
                        painter = painterResource(drawables.redo),
                        contentDescription = "Redo",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Redo", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = onFinish) { Text("Close") }
            }
        }
    }
}
