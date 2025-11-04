package com.rk.lsp

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import com.rk.file.FileObject
import com.rk.file.sandboxHomeDir
import com.rk.file.toFileObject
import com.rk.file.toFileWrapper
import com.rk.utils.toast
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.tabs.EditorTab
import com.rk.activities.main.MainViewModel
import com.rk.components.CodeItem
import com.rk.file.child
import com.rk.file.sandboxDir
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lsp.events.EventType
import io.github.rosemoe.sora.lsp.events.document.applyEdits
import io.github.rosemoe.sora.lsp.events.format.fullFormatting
import io.github.rosemoe.sora.lsp.events.format.rangeFormatting
import io.github.rosemoe.sora.widget.component.TextActionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Range
import java.io.File
import kotlin.text.substring

/**
 * Workaround helper that fixes the URI path coming from LSP if they point to `/home`.
 *
 * Example: The LSP may return `file:///home/...` but Xed-Editor has to resolve the path to `file:///data/user/0/com.rk.xededitor/local/sandbox/home/...`
 * */
fun fixHomeLocation(context: Context, uri: String): String {
    val path = uri.toUri().path ?: return uri

    val fixedPath  = when {
        path.startsWith("/home") -> {
            File(sandboxHomeDir(context), uri.toUri().path!!.removePrefix("/home/"))
        }
        path.startsWith("/usr") -> {
            File(sandboxDir(context).child("usr"), uri.toUri().path!!.removePrefix("/usr/"))
        }
        else -> null
    }

    return fixedPath?.let { Uri.fromFile(it).toString() } ?: uri
}

/**
 * Generates a text portion of the line in the provided file that contains the range.
 * The text of the range is printed bold.
 * */
suspend fun generateSnippet(viewModel: MainViewModel, targetFile: FileObject, range: Range): AnnotatedString {
    return withContext(Dispatchers.Default) {
        val openedTab = viewModel.tabs.find {
            it is EditorTab && it.file.getCanonicalPath() == targetFile.getCanonicalPath()
        } as? EditorTab

        // Do only read file if it's not already opened as a tab
        val lines = if (openedTab != null) {
            openedTab.editorState.editor.get()?.text.toString().lines()
        } else {
            targetFile.getInputStream().bufferedReader().use {
                it.readLines()
            }
        }

        val targetLine = lines[range.start.line]

        buildAnnotatedString {
            append(targetLine.take(range.start.character).trimStart())
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold ))
            append(targetLine.substring(range.start.character, range.end.character))
            pop()
            append(targetLine.drop(range.end.character).trimEnd())
        }
    }
}

/**
 * Go to or open tab that contains the range and select it.
 * */
suspend fun goToTabAndSelect(viewModel: MainViewModel, file: FileObject, range: Range) {
    withContext(Dispatchers.Main) {
        viewModel.newTab(file, switchToTab = true)
    }

    val targetTab = viewModel.tabs.filterIsInstance<EditorTab>().find { it.file == file }

    // Wait until editor content is loaded
    targetTab!!.editorState.contentRendered.await()

    withContext(Dispatchers.Main) {
        targetTab.editorState.editor.get()?.setSelectionRegion(
            range.start.line,
            range.start.character,
            range.end.line,
            range.end.character,
            SelectionChangeEvent.CAUSE_SEARCH
        )

        targetTab.editorState.editor.get()?.ensureSelectionVisible()
    }
}

fun goToDefinition(
    scope: CoroutineScope,
    context: Context,
    viewModel: MainViewModel,
    editorTab: EditorTab
) {
    scope.launch(Dispatchers.Default) {
        runCatching {
            val baseLspConnector = editorTab.baseLspConnector!!
            val editorState = editorTab.editorState
            val editor = editorState.editor.get()!!

            val eitherDefinitions = baseLspConnector.requestDefinition(editor)
            val definitions = if (eitherDefinitions.isLeft) eitherDefinitions.left else eitherDefinitions.right

            if (definitions.isEmpty()) {
                toast(strings.no_definitions_found)
                return@launch
            }

            // If only one definition exists, immediately view definition
            if (definitions.size == 1) {
                val range = if (eitherDefinitions.isLeft) eitherDefinitions.left[0].range else eitherDefinitions.right[0].targetSelectionRange
                var uriString = if (eitherDefinitions.isLeft) eitherDefinitions.left[0].uri else eitherDefinitions.right[0].targetUri
                uriString = fixHomeLocation(context, uriString)

                val uri = uriString.toUri()
                val targetFile = if (uri.scheme == null) File(uriString).toFileWrapper() else uri.toFileObject(true)

                scope.launch { goToTabAndSelect(viewModel, targetFile, range) }
                return@launch
            }

            // If multiple definitions exist, ask user which one to view
            withContext(Dispatchers.Main) {
                editorState.findingsItems = definitions.mapIndexed { index, definition ->
                    val range = if (eitherDefinitions.isLeft) eitherDefinitions.left[index].range else eitherDefinitions.right[index].targetSelectionRange
                    var uriString = if (eitherDefinitions.isLeft) eitherDefinitions.left[index].uri else eitherDefinitions.right[index].targetUri
                    uriString = fixHomeLocation(context, uriString)

                    val uri = uriString.toUri()
                    val targetFile = if (uri.scheme == null) File(uriString).toFileWrapper() else uri.toFileObject(true)

                    CodeItem(
                        snippet = generateSnippet(viewModel, targetFile, range),
                        fileName = targetFile.getName(),
                        line = range.start.line + 1,
                        column = range.start.character + 1,
                        onClick = { scope.launch { goToTabAndSelect(viewModel, targetFile, range) } }
                    )
                }
            }
            editorState.findingsTitle = strings.go_to_definition.getString()
            editorState.findingsDescription = strings.go_to_definition_desc.getString()
            editorState.showFindingsDialog = true
        }.onFailure {
            it.printStackTrace()
            toast(strings.find_definitions_error)
        }
    }
}

fun goToReferences(
    scope: CoroutineScope,
    context: Context,
    viewModel: MainViewModel,
    editorTab: EditorTab
) {
    scope.launch(Dispatchers.Default) {
        runCatching {
            val baseLspConnector = editorTab.baseLspConnector!!
            val editorState = editorTab.editorState
            val editor = editorState.editor.get()!!

            val references = baseLspConnector.requestReferences(editor)

            if (references.isEmpty()) {
                toast(strings.no_references_found)
                return@launch
            }

            // If only one reference exists, immediately view reference
            if (references.size == 1) {
                val range = references[0]!!.range
                var uriString = references[0]!!.uri
                uriString = fixHomeLocation(context, uriString)

                val uri = uriString.toUri()
                val targetFile =
                    if (uri.scheme == null) File(uriString).toFileWrapper() else uri.toFileObject(
                        true
                    )

                scope.launch { goToTabAndSelect(viewModel, targetFile, range) }
                return@launch
            }

            // If multiple references exist, ask user which one to view
            withContext(Dispatchers.Main) {
                editorState.findingsItems = references.mapIndexed { index, reference ->
                    val range = references[index]!!.range
                    var uriString = references[index]!!.uri
                    uriString = fixHomeLocation(context, uriString)

                    val uri = uriString.toUri()
                    val targetFile =
                        if (uri.scheme == null) File(uriString).toFileWrapper() else uri.toFileObject(
                            true
                        )

                    CodeItem(
                        snippet = generateSnippet(viewModel, targetFile, range),
                        fileName = targetFile.getName(),
                        line = range.start.line + 1,
                        column = range.start.character + 1,
                        onClick = {
                            scope.launch {
                                goToTabAndSelect(
                                    viewModel,
                                    targetFile,
                                    range
                                )
                            }
                        }
                    )
                }
            }
            editorState.findingsTitle = strings.go_to_references.getString()
            editorState.findingsDescription = strings.go_to_references_desc.getString()
            editorState.showFindingsDialog = true
        }.onFailure {
            it.printStackTrace()
            toast(strings.find_references_error)
        }
    }
}

fun renameSymbol(
    scope: CoroutineScope,
    editorTab: EditorTab
) {
    scope.launch(Dispatchers.Default) {
        runCatching {
            var currentName = ""

            val file = editorTab.file
            val baseLspConnector = editorTab.baseLspConnector!!
            val editorState = editorTab.editorState
            val editor = editorState.editor.get()!!

            if (baseLspConnector.isPrepareRenameSymbolSupported()) {
                val prepareRename = baseLspConnector.requestPrepareRenameSymbol(editor)

                if (prepareRename == null) {
                    toast(strings.cannot_rename_symbol)
                    return@launch
                }

                if (prepareRename.isFirst && prepareRename.first!!.start.line == prepareRename.first!!.end.line) {
                    currentName = editor.text.getLineString(prepareRename.first!!.start.line)
                        .substring(
                            prepareRename.first!!.start.character,
                            prepareRename.first!!.end.character
                        )
                }

                if (prepareRename.isSecond) {
                    currentName = prepareRename.second!!.placeholder
                }

                if (prepareRename.isThird && editor.cursor.range.start.line == editor.cursor.range.end.line) {
                    currentName = editor.text.getLineString(editor.cursor.range.start.line)
                        .substring(
                            editor.cursor.range.start.column,
                            editor.cursor.range.end.column
                        )
                }
            }

            editorState.renameValue = currentName
            editorState.showRenameDialog = true
            editorState.renameConfirm = { newName ->
                scope.launch(Dispatchers.Default) {
                    val workspaceEdit = baseLspConnector.requestRenameSymbol(editor, newName)

                    // TODO: Handle documentChanges too
                    val changes = workspaceEdit.changes

                    // Edits only supported in currently opened file
                    // TODO: Support edits in other files
                    if (changes.size > 1) {
                        toast(strings.rename_symbol_multiple_files)
                        return@launch
                    }

                    val edits = changes[file.toUri().toString()]!!
                    baseLspConnector.getEventManager()!!
                        .emitAsync(EventType.applyEdits) {
                            put("edits", edits)
                            put(editor.text)
                        }
                }
            }
        }.onFailure {
            it.printStackTrace()
            toast(strings.rename_symbol_error)
        }
    }
}

/**
 * A suspendable variant of [formatDocument] for use cases that require formatting to be complete
 * before another action is performed, such as `Format on Save`.
 * */
suspend fun formatDocumentSuspend(editorTab: EditorTab) {
    runCatching {
        val baseLspConnector = editorTab.baseLspConnector!!
        val editorState = editorTab.editorState
        val editor = editorState.editor.get()!!

        baseLspConnector.getEventManager()!!
            .emitAsync(EventType.fullFormatting, editor.text)
    }.onFailure {
        it.printStackTrace()
        toast(strings.format_error)
    }
}

fun formatDocument(
    scope: CoroutineScope,
    editorTab: EditorTab
) {
    scope.launch(Dispatchers.Default) {
        formatDocumentSuspend(editorTab = editorTab)
    }
}

fun formatDocumentRange(
    scope: CoroutineScope,
    editorTab: EditorTab
) {
    scope.launch(Dispatchers.Default) {
        runCatching {
            val baseLspConnector = editorTab.baseLspConnector!!
            val editorState = editorTab.editorState
            val editor = editorState.editor.get()!!

            baseLspConnector.getEventManager()!!
                .emitAsync(EventType.rangeFormatting) {
                    put("text", editor.text)
                    put("range", editor.cursor.range)
                }

        }.onFailure {
            it.printStackTrace()
            toast(strings.format_range_error)
        }
    }
}

/**
 * Returns a list of registerable LSP text actions.
 * */
fun createLspTextActions(
    scope: CoroutineScope,
    context: Context,
    viewModel: MainViewModel,
    editorTab: EditorTab
): List<TextActionItem> {

    val goToDefinition = TextActionItem(
        titleRes = strings.go_to_definition,
        iconRes = drawables.jump_to_element,
        shouldShow = { _ -> editorTab.baseLspConnector?.isGoToDefinitionSupported() == true },
    ) { _ -> goToDefinition(scope, context, viewModel, editorTab) }

    val goToReferences = TextActionItem(
        titleRes = strings.go_to_references,
        iconRes = drawables.manage_search,
        shouldShow = { _ -> editorTab.baseLspConnector?.isGoToReferencesSupported() == true },
    ) { _ -> goToReferences(scope, context, viewModel, editorTab) }

    val renameSymbol = TextActionItem(
        titleRes = strings.rename_symbol,
        iconRes = drawables.edit_note,
        shouldShow = { editor -> editor.isEditable && editorTab.baseLspConnector?.isRenameSymbolSupported() == true },
    ) { _ -> renameSymbol(scope, editorTab) }

    return listOf(goToDefinition, goToReferences, renameSymbol)
}