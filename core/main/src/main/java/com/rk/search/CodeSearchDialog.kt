package com.rk.search

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.rk.activities.main.MainViewModel
import com.rk.components.SingleInputDialog
import com.rk.components.XedDialog
import com.rk.components.compose.utils.addIf
import com.rk.file.FileObject
import com.rk.filetree.FileIcon
import com.rk.resources.drawables
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings
import com.rk.utils.rememberNumberFormatter
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@Composable
fun CodeSearchDialog(
    mainViewModel: MainViewModel,
    searchViewModel: SearchViewModel,
    projectFile: FileObject,
    onFinish: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val screenHeight = LocalWindowInfo.current.containerSize.height.dp

    LaunchedEffect(
        searchViewModel.isIndexing(projectFile),
        searchViewModel.codeSearchQuery,
        searchViewModel.ignoreCase,
        searchViewModel.excludedFilesText,
    ) {
        searchViewModel.launchCodeSearch(context, mainViewModel, projectFile)
    }

    if (searchViewModel.showExcludeFilesDialog) {
        ExcludeFilesDialog(searchViewModel)
    }

    XedDialog(onDismissRequest = onFinish, modifier = Modifier.imePadding()) {
        Column(modifier = Modifier.animateContentSize().height(screenHeight * 0.8f)) {
            TextField(
                value = searchViewModel.codeSearchQuery,
                onValueChange = { searchViewModel.codeSearchQuery = it },
                maxLines = 1,
                leadingIcon = {
                    IconButton(modifier = Modifier, onClick = { searchViewModel.toggleReplaceShown() }) {
                        Icon(
                            imageVector =
                                if (searchViewModel.isReplaceShown) {
                                    Icons.Outlined.KeyboardArrowUp
                                } else {
                                    Icons.Outlined.KeyboardArrowDown
                                },
                            null,
                        )
                    }
                },
                trailingIcon = {
                    Box {
                        IconButton(onClick = { searchViewModel.showOptionsMenu = true }) {
                            Icon(imageVector = Icons.Outlined.MoreVert, stringResource(strings.more))
                        }

                        DropdownMenu(
                            expanded = searchViewModel.showOptionsMenu,
                            onDismissRequest = { searchViewModel.showOptionsMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = searchViewModel.ignoreCase, onCheckedChange = null)
                                        Spacer(Modifier.width(12.dp))
                                        Text(stringResource(strings.ignore_case))
                                        Spacer(Modifier.width(8.dp))
                                    }
                                },
                                onClick = {
                                    searchViewModel.ignoreCase = !searchViewModel.ignoreCase
                                    searchViewModel.showOptionsMenu = false
                                },
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(Modifier.width(12.dp))
                                        Text(stringResource(strings.exclude_files))
                                        Spacer(Modifier.width(8.dp))
                                    }
                                },
                                onClick = {
                                    searchViewModel.showExcludeFilesDialog = true
                                    searchViewModel.showOptionsMenu = false
                                },
                            )
                        }
                    }
                },
                keyboardOptions =
                    KeyboardOptions(
                        imeAction =
                            if (searchViewModel.isReplaceShown) {
                                ImeAction.Next
                            } else {
                                ImeAction.Search
                            }
                    ),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(text = stringResource(strings.search)) },
            )

            if (searchViewModel.isReplaceShown) {
                TextField(
                    value = searchViewModel.codeReplaceQuery,
                    onValueChange = { searchViewModel.codeReplaceQuery = it },
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    placeholder = { Text(text = stringResource(strings.replace)) },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                if (searchViewModel.isIndexing(projectFile) || searchViewModel.isSearchingCode) {
                    CircularProgressIndicator(modifier = Modifier.size(9.dp), strokeWidth = 2.dp)
                }
                val numberFormatter = rememberNumberFormatter()
                val resultCount by remember {
                    derivedStateOf { numberFormatter.format(searchViewModel.codeSearchResults.size) }
                }
                Text(
                    stringResource(
                            when {
                                searchViewModel.isIndexing(projectFile) -> strings.indexing
                                searchViewModel.codeSearchResults.isNotEmpty() -> strings.results
                                else -> strings.no_results
                            }
                        )
                        .fillPlaceholders(resultCount)
                )
            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            fun replace(codeItem: CodeItem) {
                searchViewModel.viewModelScope.launch {
                    searchViewModel.replaceIn(context, mainViewModel, projectFile, codeItem)
                }
            }

            fun replaceAll(codeItems: List<CodeItem>) {
                searchViewModel.viewModelScope.launch {
                    for (codeItem in codeItems) {
                        searchViewModel.replaceIn(context, mainViewModel, projectFile, codeItem)
                    }
                }
            }

            if (searchViewModel.codeSearchQuery.isNotEmpty()) {
                LazyColumn {
                    searchViewModel.groupedCodeResults.entries.forEachIndexed { index, (fileObject, codeItems) ->
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier.addIf(codeItems.first().isHidden) { alpha(0.5f) }
                                        .padding(
                                            start = 16.dp,
                                            end = 8.dp,
                                            top = if (index > 0) 16.dp else 0.dp,
                                            bottom = 4.dp,
                                        ),
                            ) {
                                FileIcon(file = fileObject, iconTint = MaterialTheme.colorScheme.primary)

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text =
                                        if (codeItems.first().isOpen) {
                                            stringResource(strings.file_name_opened)
                                                .fillPlaceholders(fileObject.getName())
                                        } else {
                                            fileObject.getName()
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )

                                if (searchViewModel.isReplaceShown) {
                                    CompositionLocalProvider(
                                        LocalContentColor provides MaterialTheme.colorScheme.primary
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier =
                                                Modifier.clip(ButtonDefaults.shape)
                                                    .clickable { replaceAll(codeItems) }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = stringResource(strings.replace_all),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )

                                            Spacer(Modifier.width(4.dp))

                                            Icon(
                                                painter = painterResource(drawables.arrow_downward),
                                                contentDescription = stringResource(strings.replace),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(items = codeItems) { codeItem ->
                            CodeItemRow(
                                item = codeItem,
                                leadingIcon =
                                    if (searchViewModel.isReplaceShown) {
                                        {
                                            Icon(
                                                painter = painterResource(drawables.find_replace),
                                                contentDescription = stringResource(strings.replace),
                                                modifier =
                                                    Modifier.clip(RoundedCornerShape(8.dp))
                                                        .clickable(onClick = { replace(codeItem) }),
                                            )
                                        }
                                    } else null,
                                onClick = {
                                    codeItem.onClick()
                                    onFinish()
                                },
                            )
                        }
                    }
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

@Composable
fun ExcludeFilesDialog(searchViewModel: SearchViewModel) {
    var excludeFilesText by remember { mutableStateOf(searchViewModel.excludedFilesText) }

    SingleInputDialog(
        title = stringResource(id = strings.exclude_files),
        inputLabel = stringResource(id = strings.exclude_files_regex),
        inputValue = excludeFilesText,
        onInputValueChange = { excludeFilesText = it },
        onConfirm = { searchViewModel.excludedFilesText = excludeFilesText },
        onFinish = { searchViewModel.showExcludeFilesDialog = false },
    )
}
