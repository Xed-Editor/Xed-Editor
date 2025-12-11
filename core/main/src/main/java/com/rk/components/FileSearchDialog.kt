package com.rk.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.utils.addIf
import com.rk.file.FileObject
import com.rk.filetree.FileIcon
import com.rk.filetree.getAppropriateName
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private suspend fun indexFilesRecursive(parent: FileObject, results: SnapshotStateList<FileObject>) {
    val childFiles = parent.listFiles()
    val context = currentCoroutineContext()
    if (!context.isActive) return

    for (file in childFiles) {
        if (!context.isActive) return

        val isHidden = file.getName().startsWith(".")
        if (isHidden && !Settings.show_hidden_files_search) continue

        results.add(file)
        yield()

        if (file.isDirectory()) {
            indexFilesRecursive(file, results)
        }
    }
}

@Composable
fun FileSearchDialog(projectFile: FileObject, onFinish: () -> Unit, onSelect: (FileObject, FileObject) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var previousQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val indexedFiles = remember { mutableStateListOf<FileObject>() }
    val filteredFiles = remember { mutableStateListOf<FileObject>() }
    var isIndexing by remember { mutableStateOf(true) }
    var isFiltering by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isIndexing = true
        indexFilesRecursive(projectFile, indexedFiles)
        isIndexing = false
    }
    LaunchedEffect(indexedFiles.size, searchQuery) {
        isFiltering = true

        val queryChanged = searchQuery != previousQuery
        previousQuery = searchQuery

        val snapshotList = indexedFiles.toList()

        val result =
            withContext(Dispatchers.Default) {
                if (searchQuery.isEmpty()) {
                    snapshotList
                } else {
                    snapshotList.filter { it.getAppropriateName().lowercase().contains(searchQuery.lowercase()) }
                }
            }

        filteredFiles.clear()
        filteredFiles.addAll(result)
        isFiltering = false

        if (queryChanged) listState.scrollToItem(0)
    }

    XedDialog(onDismissRequest = onFinish, modifier = Modifier.imePadding()) {
        Column(modifier = Modifier.animateContentSize()) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                maxLines = 1,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(text = stringResource(strings.enter_name)) },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp, start = 16.dp, bottom = 0.dp, end = 0.dp),
            ) {
                if (isIndexing || isFiltering) {
                    CircularProgressIndicator(modifier = Modifier.size(9.dp), strokeWidth = 2.dp)
                }
                Text(
                    stringResource(
                            when {
                                filteredFiles.isNotEmpty() -> strings.results
                                else -> strings.no_results
                            }
                        )
                        .fillPlaceholders(filteredFiles.size)
                )
            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            LazyColumn(state = listState, modifier = Modifier.padding(vertical = 8.dp)) {
                items(items = filteredFiles, key = { it }) { file ->
                    Box(modifier = Modifier.animateItem()) { SearchItem(file, projectFile, onFinish, onSelect) }
                }
            }
        }
    }
}

@Composable
fun SearchItem(
    fileObject: FileObject,
    projectFile: FileObject,
    onDismissRequest: () -> Unit,
    onSelect: (FileObject, FileObject) -> Unit,
) {
    val isHidden = fileObject.getName().startsWith(".") || fileObject.getAbsolutePath().contains("/.")

    Column {
        PreferenceTemplate(
            modifier =
                Modifier.clickable(
                    enabled = true,
                    onClick = {
                        onDismissRequest()
                        onSelect(projectFile, fileObject)
                    },
                ),
            verticalPadding = 8.dp,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.addIf(isHidden) { Modifier.alpha(0.5f) }) { FileIcon(file = fileObject) }

                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(text = fileObject.getAppropriateName())
                        Text(
                            text = "." + fileObject.getAbsolutePath().removePrefix(projectFile.getAbsolutePath()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            },
            endWidget = {},
        )
    }
}
