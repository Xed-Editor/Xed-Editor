package com.rk.components

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.components.compose.utils.addIf
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.filetree.FileIcon
import com.rk.lsp.goToTabAndSelect
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.theme.currentTheme
import com.rk.utils.getSelectionColor
import com.rk.utils.isDarkMode
import com.rk.utils.toAnnotatedString
import io.github.rosemoe.sora.lsp.editor.text.MarkdownCodeHighlighterRegistry
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

val binaryExtensions: Set<String> =
    FileType.IMAGE.extensions.toSet() +
        FileType.AUDIO.extensions.toSet() +
        FileType.VIDEO.extensions.toSet() +
        FileType.ARCHIVE.extensions.toSet() +
        FileType.APK.extensions.toSet() +
        FileType.EXECUTABLE.extensions.toSet()

private fun hasBinaryChars(fileContent: String): Boolean {
    val checkedCharacters = 1024
    val threshold = 0.3

    val checkText = fileContent.take(checkedCharacters)
    val total = checkText.length
    if (total == 0) return false

    val binarySymbolsCount =
        checkText.count { c ->
            (c.code < 32 && c.code != 9 && c.code != 10 && c.code != 12 && c.code != 13) || c.code > 126
        }

    // If the amount of binary chars in the file content is over 30%
    return binarySymbolsCount.toDouble() / total > threshold
}

private suspend fun findCodeRecursive(
    mainViewModel: MainViewModel,
    scope: CoroutineScope,
    parent: FileObject,
    query: String,
    results: SnapshotStateMap<FileObject, MutableList<CodeItem>>,
) {
    val childFiles = parent.listFiles()
    val context = currentCoroutineContext()
    val openedEditorTabs = mainViewModel.tabs.mapNotNull { it as? EditorTab }
    if (!context.isActive) return

    for (file in childFiles) {
        if (!context.isActive) return

        val isHidden = file.getName().startsWith(".")
        if (isHidden && !Settings.show_hidden_files_search) continue

        if (file.isDirectory()) {
            findCodeRecursive(mainViewModel, scope, file, query, results)
            continue
        }

        if (file.length() > 10_000_000) continue // Do not search in file if it's over 10MB
        val ext = file.getName().substringAfterLast(".", "")
        if (ext.lowercase() in binaryExtensions) {
            continue // Do not search in file if it's likely to be binary (file extension based detection)
        }

        val openedTab = openedEditorTabs.find { it.file == file }
        val lines =
            if (openedTab != null) {
                openedTab.editorState.editor.get()?.text.toString().lines()
            } else {
                val fileText = file.readText()
                if (hasBinaryChars(fileText ?: "")) {
                    continue // Do not search in file if it's likely to be binary (character based detection)
                }
                fileText?.lines() ?: emptyList()
            }

        lines.forEachIndexed { lineIndex, line ->
            if (line.lowercase().contains(query.lowercase())) {
                val charIndex = line.indexOf(query)
                val fileExt = file.getName().substringAfterLast(".")

                val codeItem =
                    CodeItem(
                        snippet =
                            // TODO: Syntax highlighting won't work before opening any tabs because it's only
                            // TODO: initialized after opening one
                            generateSnippet(
                                context = MainActivity.instance!!,
                                targetLine = line,
                                fileExt = fileExt,
                                start = charIndex,
                                end = charIndex + query.length,
                            ),
                        fileName = file.getName(),
                        line = lineIndex + 1,
                        column = charIndex + 1,
                        onClick = {
                            DefaultScope.launch {
                                goToTabAndSelect(
                                    viewModel = mainViewModel,
                                    file = file,
                                    range =
                                        Range(
                                            Position(lineIndex, charIndex),
                                            Position(lineIndex, charIndex + query.length),
                                        ),
                                )
                            }
                        },
                    )

                results[file]?.add(codeItem) ?: run { results[file] = mutableListOf(codeItem) }
            }
        }
    }
}

suspend fun generateSnippet(
    context: Context,
    targetLine: String,
    fileExt: String,
    start: Int,
    end: Int,
): AnnotatedString {
    return withContext(Dispatchers.Default) {
        val trimmedTargetLine = targetLine.trim()
        val leadingWhitespace = targetLine.indexOf(trimmedTargetLine)

        val rangeStartTrimmed = start - leadingWhitespace
        val rangeEndTrimmed = end - leadingWhitespace

        val highlightedSpanned =
            MarkdownCodeHighlighterRegistry.global.highlightAsync(
                code = trimmedTargetLine,
                language = fileExt,
                codeTypeface = Typeface.MONOSPACE,
            )

        val highlightedAnnotated = (highlightedSpanned as? Spannable)?.toAnnotatedString() ?: highlightedSpanned

        val editorColors =
            if (isDarkMode(context)) {
                currentTheme.value?.darkEditorColors
            } else {
                currentTheme.value?.lightEditorColors
            }
        val selectionColor =
            editorColors?.find { it.key == EditorColorScheme.SELECTED_TEXT_BACKGROUND }?.color?.let { Color(it) }
                ?: getSelectionColor()

        buildAnnotatedString {
            append(highlightedAnnotated)
            addStyle(style = SpanStyle(background = selectionColor), start = rangeStartTrimmed, end = rangeEndTrimmed)
        }
    }
}

@Composable
fun CodeSearchDialog(viewModel: MainViewModel, projectFile: FileObject, onFinish: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val filteredCode = remember { mutableStateMapOf<FileObject, MutableList<CodeItem>>() }
    var isIndexing by remember { mutableStateOf(true) }

    LaunchedEffect(searchQuery) {
        delay(250)

        isIndexing = true
        filteredCode.clear()
        if (searchQuery.isNotEmpty()) {
            findCodeRecursive(viewModel, scope, projectFile, searchQuery, filteredCode)
        }
        isIndexing = false

        listState.scrollToItem(0)
    }

    val resultsCount by remember { derivedStateOf { filteredCode.values.sumOf { it.size } } }

    XedDialog(onDismissRequest = onFinish, modifier = Modifier.imePadding()) {
        Column(modifier = Modifier.animateContentSize()) {
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
                modifier = Modifier.padding(top = 16.dp, start = 16.dp, bottom = 0.dp, end = 0.dp),
            ) {
                if (isIndexing) {
                    CircularProgressIndicator(modifier = Modifier.size(9.dp), strokeWidth = 2.dp)
                }
                Text(
                    stringResource(
                            when {
                                searchQuery.isEmpty() -> strings.empty_code_results
                                filteredCode.isNotEmpty() -> strings.results
                                else -> strings.no_results
                            }
                        )
                        .fillPlaceholders(resultsCount)
                )
            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            LazyColumn(state = listState, modifier = Modifier.padding(all = 16.dp)) {
                filteredCode.forEach { (fileObject, codeItems) ->
                    val isHidden = fileObject.getName().startsWith(".") || fileObject.getAbsolutePath().contains("/.")

                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier.addIf(isHidden) { Modifier.alpha(0.5f) }.padding(top = 8.dp, bottom = 4.dp),
                        ) {
                            FileIcon(file = fileObject, iconTint = MaterialTheme.colorScheme.primary)

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = fileObject.getName(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    items(codeItems) { codeItem ->
                        Box(modifier = Modifier.addIf(isHidden) { Modifier.alpha(0.5f) }) {
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
            }
        }
    }
}
