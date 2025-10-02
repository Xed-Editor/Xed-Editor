package com.rk.xededitor.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.tabs.CodeEditorState
import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.subscribeAlways
import java.util.regex.PatternSyntaxException

@Composable
fun SearchPanel(
    editorState: CodeEditorState,
    modifier: Modifier = Modifier,
) {
    val editor = editorState.editor
    val focusRequester = remember { FocusRequester() }

    // Search error state
    var hasSearchError by remember { mutableStateOf(false) }
    var isSearchingInternal by remember { mutableStateOf(false) }
    var searchMatchesCount by remember { mutableIntStateOf(0) }
    var searchCurrentIndex by remember { mutableIntStateOf(0) }

    // Search execution logic
    fun tryCommitSearch() {
        val query = editorState.searchKeyword
        if (query.isNotEmpty()) {
            try {
                val searchOptions = getSearchOptions(editorState.ignoreCase, editorState.searchRegex, editorState.searchWholeWord)
                editor?.searcher?.search(query, searchOptions)
                hasSearchError = false
                isSearchingInternal = true
            } catch (e: PatternSyntaxException) {
                hasSearchError = true
                isSearchingInternal = false
            }
        } else {
            editor?.searcher?.stopSearch()
            hasSearchError = false
            isSearchingInternal = false
        }
    }

    LaunchedEffect(editor) {
        editor?.subscribeAlways(PublishSearchResultEvent::class.java) {
            searchMatchesCount = runCatching { editor.searcher?.matchedPositionCount ?: 0 }.getOrDefault(0)
        }

        editor?.subscribeAlways(SelectionChangeEvent::class.java) {
            searchCurrentIndex = runCatching { editor.searcher?.currentMatchedPositionIndex?.plus(1) ?: 0 }.getOrDefault(0)
        }
    }

    // Execute search when keyword changes
    LaunchedEffect(editorState.isSearching,editorState.searchKeyword, editorState.ignoreCase, editorState.searchRegex, editorState.searchWholeWord) {
        if (editorState.isSearching){
            tryCommitSearch()
        }
    }

    LaunchedEffect(editorState.isSearching) {
        if (editorState.isSearching) {
            focusRequester.requestFocus()
        }
    }

    if (editorState.isSearching) {
        Column(
            modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Row(
                Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {

                    Row(modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            modifier = Modifier,
                            onClick = {
                                editorState.isReplaceShown = !editorState.isReplaceShown
                            }
                        ) {
                            Icon(
                                imageVector = if (editorState.isReplaceShown) {
                                    Icons.Outlined.KeyboardArrowUp
                                } else {
                                    Icons.Outlined.KeyboardArrowDown
                                }, null
                            )
                        }

                        StyledTextField(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .padding(horizontal = 8.dp)
                                .focusRequester(focusRequester),
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 1,
                            // Show error state with red text color
                            textStyle = LocalTextStyle.current.copy(
                                color = if (hasSearchError) MaterialTheme.colorScheme.error else LocalContentColor.current
                            ),
                            trailingIcon = {
                                Box {
                                    IconButton(
                                        modifier = Modifier.height(24.dp),
                                        onClick = { editorState.showOptionsMenu = true }
                                    ) {
                                        Icon(imageVector = Icons.Outlined.MoreVert, null)
                                    }

                                    DropdownMenu(
                                        expanded = editorState.showOptionsMenu,
                                        onDismissRequest = { editorState.showOptionsMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = editorState.ignoreCase,
                                                        onCheckedChange = { editorState.ignoreCase = it }
                                                    )
                                                    Text(stringResource(strings.ignore_case))
                                                    Spacer(Modifier.width(8.dp))
                                                }
                                            },
                                            onClick = {
                                                editorState.ignoreCase = !editorState.ignoreCase
                                                editorState.showOptionsMenu = false
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = editorState.searchRegex,
                                                        onCheckedChange = {
                                                            editorState.searchRegex = it
                                                            if (it) {
                                                                editorState.searchWholeWord = false
                                                            }
                                                        }
                                                    )
                                                    Text(stringResource(strings.regex))
                                                    Spacer(Modifier.width(8.dp))
                                                }
                                            },
                                            onClick = {
                                                val newValue = !editorState.searchRegex
                                                editorState.searchRegex = newValue
                                                editorState.showOptionsMenu = false
                                                if (newValue) {
                                                    editorState.searchWholeWord = false
                                                }
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = editorState.searchWholeWord,
                                                        onCheckedChange = {
                                                            editorState.searchWholeWord = it
                                                            if (it) {
                                                                editorState.searchRegex = false
                                                            }
                                                        }
                                                    )
                                                    Text(stringResource(strings.whole_word))
                                                    Spacer(Modifier.width(8.dp))
                                                }
                                            },
                                            onClick = {
                                                val newValue = !editorState.searchWholeWord;
                                                editorState.searchWholeWord = newValue
                                                editorState.showOptionsMenu = false
                                                if (newValue) {
                                                    editorState.searchRegex = false
                                                }
                                            }
                                        )
                                    }
                                }
                            },
                            value = editorState.searchKeyword,
                            onValueChange = {
                                editorState.searchKeyword = it
                                tryCommitSearch()
                            },
                            placeholder = { Text(stringResource(strings.search)) },
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (editorState.isReplaceShown) {
                                    ImeAction.Next
                                } else {
                                    ImeAction.Search
                                }
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = { tryCommitSearch() },
                                onNext = { tryCommitSearch() }
                            )
                        )

                        IconButton(onClick = {
                            editorState.isSearching = false
                            editor?.searcher?.stopSearch()
                        }) {
                            Icon(imageVector = Icons.Outlined.Close, null)
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    if (editorState.isReplaceShown) {
                        Row(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.width(48.dp))

                            StyledTextField(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .height(42.dp),
                                value = editorState.replaceKeyword,
                                onValueChange = { editorState.replaceKeyword = it },
                                shape = RoundedCornerShape(8.dp),
                                placeholder = { Text(stringResource(strings.replace)) },
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Default
                                ),
                            )

                            Spacer(Modifier.width(48.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp)
                ) {
                    TextButton(enabled = isSearchingInternal, onClick = {
                        editor?.searcher?.gotoPrevious()
                    }) {
                        Text(stringResource(strings.go_prev).uppercase())
                    }

                    Spacer(Modifier.height(10.dp))

                    TextButton(enabled = isSearchingInternal, onClick = {
                        editor?.searcher?.gotoNext()
                    }) {
                        Text(stringResource(strings.go_next).uppercase())
                    }

                    if (editorState.isReplaceShown) {
                        TextButton(
                            enabled = isSearchingInternal,
                            onClick = {
                                editor?.searcher?.replaceCurrentMatch(editorState.replaceKeyword)
                            }) {
                            Text(stringResource(strings.replace).uppercase())
                        }

                        TextButton(
                            enabled = isSearchingInternal,
                            onClick = {
                                editor?.searcher?.replaceAll(editorState.replaceKeyword)
                            }) {
                            Text(stringResource(strings.replace_all).uppercase())
                        }
                    }
                }

                Text(
                    text = "$searchCurrentIndex/$searchMatchesCount",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                )
            }
        }

        BackHandler {
            editorState.isSearching = false
            editor?.searcher?.stopSearch()
        }
    }
}

// Helper function to create search options (similar to the XML version)
private fun getSearchOptions(
    ignoreCase: Boolean,
    searchRegex: Boolean,
    searchWholeWord: Boolean
): EditorSearcher.SearchOptions {
    val caseInsensitive = ignoreCase
    var type = EditorSearcher.SearchOptions.TYPE_NORMAL

    if (searchRegex) {
        type = EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
    }

    if (searchWholeWord) {
        type = EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
    }

    return EditorSearcher.SearchOptions(type, caseInsensitive)
}