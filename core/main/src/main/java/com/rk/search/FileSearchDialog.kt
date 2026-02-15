package com.rk.search

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.components.XedDialog
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.utils.addIf
import com.rk.file.FileObject
import com.rk.file.toFileWrapper
import com.rk.filetree.FileIcon
import com.rk.filetree.getAppropriateName
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.utils.getGitColor
import java.io.File

@Composable
fun FileSearchDialog(
    searchViewModel: SearchViewModel,
    projectFile: FileObject,
    onFinish: () -> Unit,
    onSelect: (FileObject, FileObject) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<FileMeta>>(emptyList()) }

    val context = LocalContext.current

    LaunchedEffect(searchViewModel.isIndexing(projectFile), searchQuery) {
        isSearching = true
        val results =
            searchViewModel.searchFileName(
                context = context,
                projectRoot = projectFile,
                query = searchQuery,
                useIndex =
                    Preference.getBoolean("enable_indexing_${projectFile.hashCode()}", Settings.always_index_projects),
            )
        searchResults = results
        isSearching = false
    }

    val screenHeight = LocalWindowInfo.current.containerSize.height.dp
    XedDialog(onDismissRequest = onFinish, modifier = Modifier.imePadding()) {
        Column(modifier = Modifier.animateContentSize().height(screenHeight * 0.8f)) {
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
                modifier = Modifier.padding(top = 16.dp, start = 16.dp, bottom = 0.dp, end = 16.dp),
            ) {
                if (searchViewModel.isIndexing(projectFile) || isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(9.dp), strokeWidth = 2.dp)
                }
                Text(
                    stringResource(
                            when {
                                searchViewModel.isIndexing(projectFile) -> strings.indexing
                                searchResults.isNotEmpty() -> strings.results
                                else -> strings.no_results
                            }
                        )
                        .fillPlaceholders(searchResults.size)
                )
            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
                items(items = searchResults, key = { it }) { codeLine ->
                    Box(modifier = Modifier.animateItem()) {
                        SearchItem(File(codeLine.path).toFileWrapper(), projectFile, onFinish, onSelect)
                    }
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
    val fileNameColor = getGitColor(fileObject) ?: Color.Unspecified

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
                    Box(modifier = Modifier.addIf(isHidden) { alpha(0.5f) }) { FileIcon(file = fileObject) }

                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(text = fileObject.getAppropriateName(), color = fileNameColor)
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
