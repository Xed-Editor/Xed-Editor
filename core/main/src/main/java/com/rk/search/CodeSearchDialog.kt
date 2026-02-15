package com.rk.search

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainViewModel
import com.rk.components.XedDialog
import com.rk.components.compose.utils.addIf
import com.rk.file.FileObject
import com.rk.filetree.FileIcon
import com.rk.resources.drawables
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.Settings
import kotlinx.coroutines.FlowPreview

@OptIn(FlowPreview::class)
@Composable
fun CodeSearchDialog(
    viewModel: MainViewModel,
    searchViewModel: SearchViewModel,
    projectFile: FileObject,
    onFinish: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    var isSearching by remember { mutableStateOf(false) }
    val searchResults = remember { mutableStateListOf<CodeItem>() }

    var groupedResults by remember { mutableStateOf<Map<FileObject, List<CodeItem>>>(emptyMap()) }

    val context = LocalContext.current

    LaunchedEffect(searchViewModel.isIndexing(projectFile), searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults.clear()
            isSearching = false
            return@LaunchedEffect
        }

        isSearching = true
        searchResults.clear()
        searchViewModel
            .searchCode(
                context = context,
                projectRoot = projectFile,
                query = searchQuery,
                mainViewModel = viewModel,
                useIndex =
                    Preference.getBoolean("enable_indexing_${projectFile.hashCode()}", Settings.always_index_projects),
            )
            .collect { searchResults.add(it) }
        isSearching = false
    }

    LaunchedEffect(searchResults.size) { groupedResults = searchResults.groupBy { it.file } }

    val screenHeight = LocalWindowInfo.current.containerSize.height.dp
    XedDialog(onDismissRequest = onFinish, modifier = Modifier.imePadding()) {
        Column(modifier = Modifier.animateContentSize().height(screenHeight * 0.8f)) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                maxLines = 1,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(text = stringResource(strings.enter_code_snippet)) },
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

            if (searchQuery.isNotEmpty()) {
                LazyColumn(modifier = Modifier.padding(all = 16.dp)) {
                    groupedResults.forEach { (fileObject, codeItems) ->
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier.addIf(codeItems.first().isHidden) { alpha(0.5f) }
                                        .padding(top = 8.dp, bottom = 4.dp),
                            ) {
                                FileIcon(file = fileObject, iconTint = MaterialTheme.colorScheme.primary)

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text =
                                        if (codeItems.first().opened) {
                                            stringResource(strings.file_name_opened)
                                                .fillPlaceholders(fileObject.getName())
                                        } else {
                                            fileObject.getName()
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        items(codeItems) { codeItem ->
                            CodeItemRow(
                                item = codeItem,
                                onClick = {
                                    codeItem.onClick()
                                    onFinish()
                                },
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(drawables.search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(strings.enter_query_to_search),
                        modifier = Modifier.fillMaxWidth(0.5f),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
