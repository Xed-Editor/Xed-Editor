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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.rk.activities.main.MainViewModel
import com.rk.components.SingleInputDialog
import com.rk.components.XedDialog
import com.rk.components.XedDropdownMenuItem
import com.rk.components.compose.utils.addIf
import com.rk.file.FileObject
import com.rk.filetree.FileIcon
import com.rk.filetree.getAppropriateName
import com.rk.resources.drawables
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
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
    val viewportHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }

    val codeSearchQuery by searchViewModel.codeSearchQuery.collectAsStateWithLifecycle()
    val codeReplaceQuery by searchViewModel.codeReplaceQuery.collectAsStateWithLifecycle()
    val isReplaceShown by searchViewModel.isReplaceShown.collectAsStateWithLifecycle()
    val showOptionsMenu by searchViewModel.showOptionsMenu.collectAsStateWithLifecycle()
    val ignoreCase by searchViewModel.ignoreCase.collectAsStateWithLifecycle()
    val isSearchingCode by searchViewModel.isSearchingCode.collectAsStateWithLifecycle()
    val totalCodeSearchResults by searchViewModel.totalCodeSearchResults.collectAsStateWithLifecycle()
    val codeSearchResultsOrder by searchViewModel.codeSearchResultsOrder.collectAsStateWithLifecycle()
    val codeSearchResults by searchViewModel.codeSearchResults.collectAsStateWithLifecycle()
    val collapsedFiles by searchViewModel.collapsedFiles.collectAsStateWithLifecycle()
    val fileMaskText by searchViewModel.fileMaskText.collectAsStateWithLifecycle()
    val showFileMaskDialog by searchViewModel.showFileMaskDialog.collectAsStateWithLifecycle()
    val isIndexingMap by searchViewModel.isIndexing.collectAsStateWithLifecycle()
    val isIndexingProject = isIndexingMap[projectFile] == true

    val editorTab = mainViewModel.currentTab as? EditorTab
    val textFieldSearchState =
        rememberTextFieldState(
            editorTab?.editorState?.editor?.get()?.getSelectedText() ?: codeSearchQuery
        )
    LaunchedEffect(textFieldSearchState.text) { searchViewModel.setCodeSearchQuery(textFieldSearchState.text.toString()) }

    val textFieldReplaceState = rememberTextFieldState(codeReplaceQuery)
    LaunchedEffect(textFieldReplaceState.text) {
        searchViewModel.setCodeReplaceQuery(textFieldReplaceState.text.toString())
    }

    LaunchedEffect(
        isIndexingProject,
        codeSearchQuery,
        ignoreCase,
        fileMaskText,
    ) {
        searchViewModel.launchCodeSearch(context, mainViewModel, projectFile)
    }

    if (showFileMaskDialog) {
        ExcludeFilesDialog(searchViewModel)
    }

    fun replace(codeItem: CodeItem) {
        searchViewModel.viewModelScope.launch {
            searchViewModel.replaceIn(mainViewModel, codeItem)
            searchViewModel.launchCodeSearch(context, mainViewModel, projectFile)
        }
    }

    fun replaceAll(codeItems: List<CodeItem>) {
        searchViewModel.viewModelScope.launch {
            searchViewModel.replaceAllIn(mainViewModel, codeItems)
            searchViewModel.launchCodeSearch(context, mainViewModel, projectFile)
        }
    }

    XedDialog(onDismissRequest = onFinish, modifier = Modifier.imePadding()) {
        Column(modifier = Modifier.animateContentSize().height(viewportHeight * 0.8f)) {
            TextField(
                state = textFieldSearchState,
                lineLimits = TextFieldLineLimits.SingleLine,
                leadingIcon = {
                    IconButton(modifier = Modifier, onClick = { searchViewModel.toggleReplaceShown() }) {
                        Icon(
                            imageVector =
                                if (isReplaceShown) {
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
                        IconButton(onClick = { searchViewModel.setShowOptionsMenu(true) }) {
                            Icon(imageVector = Icons.Outlined.MoreVert, stringResource(strings.more))
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { searchViewModel.setShowOptionsMenu(false) },
                        ) {
                            XedDropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = ignoreCase, onCheckedChange = null)
                                        Spacer(Modifier.width(12.dp))
                                        Text(stringResource(strings.ignore_case))
                                        Spacer(Modifier.width(8.dp))
                                    }
                                },
                                onClick = {
                                    searchViewModel.setIgnoreCase(!ignoreCase)
                                    searchViewModel.setShowOptionsMenu(false)
                                },
                            )

                            XedDropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(painter = painterResource(drawables.edit), contentDescription = null)
                                        Spacer(Modifier.width(12.dp))
                                        Text(stringResource(strings.file_mask))
                                        Spacer(Modifier.width(8.dp))
                                    }
                                },
                                onClick = {
                                    searchViewModel.setShowFileMaskDialog(true)
                                    searchViewModel.setShowOptionsMenu(false)
                                },
                            )
                        }
                    }
                },
                keyboardOptions =
                    KeyboardOptions(
                        imeAction =
                            if (isReplaceShown) {
                                ImeAction.Next
                            } else {
                                ImeAction.Search
                            }
                    ),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(text = stringResource(strings.search)) },
                supportingText =
                    if (!isReplaceShown) {
                        {
                            Text(
                                text =
                                    stringResource(strings.searching_in)
                                        .fillPlaceholders(projectFile.getAppropriateName()),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else null,
            )

            if (isReplaceShown) {
                TextField(
                    state = textFieldReplaceState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    placeholder = { Text(text = stringResource(strings.replace)) },
                    shape = RectangleShape,
                    trailingIcon = {
                        IconButton(
                            enabled = totalCodeSearchResults != 0,
                            onClick = { replaceAll(codeSearchResults.values.flatten()) },
                        ) {
                            Icon(
                                painter = painterResource(drawables.find_replace),
                                contentDescription = stringResource(strings.replace),
                            )
                        }
                    },
                    supportingText = {
                        Text(
                            text =
                                stringResource(strings.searching_in).fillPlaceholders(projectFile.getAppropriateName()),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier.padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 8.dp,
                    ),
            ) {
                if (isIndexingProject || isSearchingCode) {
                    CircularProgressIndicator(modifier = Modifier.size(9.dp), strokeWidth = 2.dp)
                }
                val numberFormatter = rememberNumberFormatter()
                val resultCount by remember {
                    derivedStateOf {
                        val amount = totalCodeSearchResults
                        val suffix = if (amount == SearchViewModel.MAX_CODE_RESULTS) "+" else ""
                        numberFormatter.format(amount) + suffix
                    }
                }
                Text(
                    stringResource(
                            when {
                                isIndexingProject -> strings.indexing
                                totalCodeSearchResults != 0 -> strings.results
                                else -> strings.no_results
                            }
                        )
                        .fillPlaceholders(resultCount)
                )
            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            if (codeSearchQuery.isNotEmpty()) {
                LazyColumn {
                    codeSearchResultsOrder.forEachIndexed { _, fileObject ->
                        val codeItems = codeSearchResults[fileObject] ?: return@forEachIndexed
                        val isCollapsed = fileObject in collapsedFiles

                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier.addIf(codeItems.first().isHidden) { alpha(0.5f) }
                                        .clickable { searchViewModel.toggleCollapsed(fileObject) }
                                        .padding(
                                            start = 16.dp,
                                            end = 8.dp,
                                            top = 8.dp,
                                            bottom = 8.dp,
                                        ),
                            ) {
                                Icon(
                                    imageVector =
                                        if (isCollapsed) Icons.AutoMirrored.Outlined.KeyboardArrowRight
                                        else Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )

                                Spacer(modifier = Modifier.width(4.dp))

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

                                if (isReplaceShown) {
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

                        if (!isCollapsed) {
                            items(items = codeItems) { codeItem ->
                                CodeItemRow(
                                    item = codeItem,
                                    leadingIcon =
                                        if (isReplaceShown) {
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
    var fileMaskText by remember { mutableStateOf(searchViewModel.fileMaskText.value) }

    SingleInputDialog(
        title = stringResource(id = strings.file_mask),
        inputLabel = stringResource(id = strings.file_mask_hint),
        inputValue = fileMaskText,
        onInputValueChange = { fileMaskText = it },
        onConfirm = {
            searchViewModel.setFileMaskText(fileMaskText)
            Settings.file_mask = fileMaskText
        },
        onFinish = {
            searchViewModel.setFileMaskText(Settings.file_mask)
            searchViewModel.setShowFileMaskDialog(false)
        },
    )
}
