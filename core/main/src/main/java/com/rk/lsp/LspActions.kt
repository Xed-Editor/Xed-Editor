package com.rk.lsp

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.rk.activities.main.MainViewModel
import com.rk.file.FileObject
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.file.toFileObject
import com.rk.file.toFileWrapper
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.search.CodeItem
import com.rk.search.SnippetBuilder
import com.rk.tabs.editor.EditorTab
import com.rk.utils.toast
import io.github.rosemoe.sora.lsp.editor.LspEventManager
import io.github.rosemoe.sora.lsp.editor.getOption
import io.github.rosemoe.sora.lsp.events.EventType
import io.github.rosemoe.sora.lsp.events.document.applyEdits
import io.github.rosemoe.sora.lsp.events.format.fullFormatting
import io.github.rosemoe.sora.lsp.events.format.rangeFormatting
import io.github.rosemoe.sora.widget.component.TextActionItem
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.Range

/**
 * Workaround helper that fixes the URI path coming from LSP if they point to `/home`.
 *
 * Example: The LSP may return `file:///home/...` but Xed-Editor has to resolve the path to
 * `file:///data/user/0/com.rk.xededitor/local/sandbox/home/...`
 */
fun fixHomeLocation(context: Context, uri: String): String {
    val path = uri.toUri().path ?: return uri

    val fixedPath =
        when {
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

suspend fun MainViewModel.goToTabAndSelect(file: FileObject, range: Range) {
    goToTabAndSelect(file, range.start.line, range.start.character, range.end.line, range.end.character)
}

fun goToDefinition(scope: CoroutineScope, context: Context, viewModel: MainViewModel, editorTab: EditorTab) {
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
                    val range =
                        if (eitherDefinitions.isLeft) eitherDefinitions.left[0].range
                        else eitherDefinitions.right[0].targetSelectionRange
                    var uriString =
                        if (eitherDefinitions.isLeft) eitherDefinitions.left[0].uri
                        else eitherDefinitions.right[0].targetUri
                    uriString = fixHomeLocation(context, uriString)

                    val uri = uriString.toUri()
                    val targetFile = if (uri.scheme == null) File(uriString).toFileWrapper() else uri.toFileObject(true)

                    scope.launch { viewModel.goToTabAndSelect(targetFile, range) }
                    return@launch
                }

                // If multiple definitions exist, ask user which one to view
                withContext(Dispatchers.Main) {
                    val snippetBuilder = SnippetBuilder(context)
                    editorState.findingsItems =
                        List(definitions.size) { index ->
                            val range =
                                if (eitherDefinitions.isLeft) eitherDefinitions.left[index].range
                                else eitherDefinitions.right[index].targetSelectionRange
                            var uriString =
                                if (eitherDefinitions.isLeft) eitherDefinitions.left[index].uri
                                else eitherDefinitions.right[index].targetUri
                            uriString = fixHomeLocation(context, uriString)

                            val uri = uriString.toUri()
                            val targetFile =
                                if (uri.scheme == null) File(uriString).toFileWrapper() else uri.toFileObject(true)

                            CodeItem(
                                snippet = snippetBuilder.generateLspSnippet(viewModel, targetFile, range),
                                file = targetFile,
                                highlightStart = range.start.character,
                                line = range.start.line + 1,
                                column = range.start.character + 1,
                                onClick = { scope.launch { viewModel.goToTabAndSelect(targetFile, range) } },
                            )
                        }
                }
                editorState.findingsTitle = strings.go_to_definition.getString()
                editorState.findingsDescription = strings.go_to_definition_desc.getString()
                editorState.showFindingsDialog = true
            }
            .onFailure {
                it.printStackTrace()
                toast(strings.find_definitions_error)
            }
    }
}

fun goToReferences(scope: CoroutineScope, context: Context, viewModel: MainViewModel, editorTab: EditorTab) {
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
                    val targetFile = if (uri.scheme == null) File(uriString).toFileWrapper() else uri.toFileObject(true)

                    scope.launch { viewModel.goToTabAndSelect(targetFile, range) }
                    return@launch
                }

                // If multiple references exist, ask user which one to view
                withContext(Dispatchers.Main) {
                    val snippetBuilder = SnippetBuilder(context)
                    editorState.findingsItems =
                        references.mapNotNull { reference ->
                            val range = reference?.range ?: return@mapNotNull null
                            var uriString = reference.uri
                            uriString = fixHomeLocation(context, uriString)

                            val uri = uriString.toUri()
                            val targetFile =
                                if (uri.scheme == null) File(uriString).toFileWrapper() else uri.toFileObject(true)

                            CodeItem(
                                snippet = snippetBuilder.generateLspSnippet(viewModel, targetFile, range),
                                file = targetFile,
                                line = range.start.line + 1,
                                column = range.start.character + 1,
                                onClick = { scope.launch { viewModel.goToTabAndSelect(targetFile, range) } },
                            )
                        }
                }
                editorState.findingsTitle = strings.go_to_references.getString()
                editorState.findingsDescription = strings.go_to_references_desc.getString()
                editorState.showFindingsDialog = true
            }
            .onFailure {
                it.printStackTrace()
                toast(strings.find_references_error)
            }
    }
}

fun renameSymbol(scope: CoroutineScope, editorTab: EditorTab) {
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
                        currentName =
                            editor.text
                                .getLineString(prepareRename.first!!.start.line)
                                .substring(prepareRename.first!!.start.character, prepareRename.first!!.end.character)
                    }

                    if (prepareRename.isSecond) {
                        currentName = prepareRename.second!!.placeholder
                    }

                    if (prepareRename.isThird && editor.cursor.range.start.line == editor.cursor.range.end.line) {
                        currentName =
                            editor.text
                                .getLineString(editor.cursor.range.start.line)
                                .substring(editor.cursor.range.start.column, editor.cursor.range.end.column)
                    }
                }

                editorState.renameValue = currentName
                editorState.showRenameDialog = true
                editorState.renameConfirm = { newName ->
                    scope.launch(Dispatchers.Default) {
                        runCatching {
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
                                baseLspConnector.getEventManager()!!.emitAsync(EventType.applyEdits) {
                                    put("edits", edits)
                                    put(editor.text)
                                }
                            }
                            .onFailure {
                                it.printStackTrace()
                                toast(strings.rename_symbol_error)
                            }
                    }
                }
            }
            .onFailure {
                it.printStackTrace()
                toast(strings.rename_symbol_error)
            }
    }
}

fun applyFormattingOptions(eventManager: LspEventManager, editorTab: EditorTab) {
    val editor = editorTab.editorState.editor.get() ?: return
    val formattingOptions = eventManager.getOption<FormattingOptions>()!!
    formattingOptions.tabSize = editor.tabWidth
    formattingOptions.isInsertSpaces = !editor.editorLanguage.useTab()
    formattingOptions.isInsertFinalNewline = editor.insertFinalNewline
    formattingOptions.isTrimTrailingWhitespace = editor.trimTrailingWhitespace
}

/**
 * A suspendable variant of [formatDocument] for use cases that require formatting to be complete before another action
 * is performed, such as `Format on Save`.
 */
suspend fun formatDocumentSuspend(editorTab: EditorTab) {
    runCatching {
            val baseLspConnector = editorTab.baseLspConnector!!
            val editorState = editorTab.editorState
            val editor = editorState.editor.get()!!
            val eventManager = baseLspConnector.getEventManager()!!

            applyFormattingOptions(eventManager, editorTab)

            eventManager.emitAsync(EventType.fullFormatting, editor.text)
        }
        .onFailure {
            it.printStackTrace()
            toast(strings.format_document_error)
        }
}

fun formatDocument(scope: CoroutineScope, editorTab: EditorTab) {
    scope.launch(Dispatchers.Default) { formatDocumentSuspend(editorTab) }
}

fun formatDocumentRange(scope: CoroutineScope, editorTab: EditorTab) {
    scope.launch(Dispatchers.Default) {
        runCatching {
                val baseLspConnector = editorTab.baseLspConnector!!
                val editorState = editorTab.editorState
                val editor = editorState.editor.get()!!
                val eventManager = baseLspConnector.getEventManager()!!

                applyFormattingOptions(eventManager, editorTab)

                eventManager.emitAsync(EventType.rangeFormatting) {
                    put("text", editor.text)
                    put("range", editor.cursor.range)
                }
            }
            .onFailure {
                it.printStackTrace()
                toast(strings.format_selection_error)
            }
    }
}

/** Returns a list of registerable LSP text actions. */
fun createLspTextActions(
    scope: CoroutineScope,
    context: Context,
    viewModel: MainViewModel,
    editorTab: EditorTab,
): List<TextActionItem> {

    val goToDefinition =
        TextActionItem(
            titleRes = strings.go_to_definition,
            iconRes = drawables.jump_to_element,
            shouldShow = { _ -> editorTab.baseLspConnector?.isGoToDefinitionSupported() == true },
        ) { _ ->
            goToDefinition(scope, context, viewModel, editorTab)
        }

    val goToReferences =
        TextActionItem(
            titleRes = strings.go_to_references,
            iconRes = drawables.manage_search,
            shouldShow = { _ -> editorTab.baseLspConnector?.isGoToReferencesSupported() == true },
        ) { _ ->
            goToReferences(scope, context, viewModel, editorTab)
        }

    val renameSymbol =
        TextActionItem(
            titleRes = strings.rename_symbol,
            iconRes = drawables.edit_note,
            shouldShow = { editor ->
                editor.isEditable && editorTab.baseLspConnector?.isRenameSymbolSupported() == true
            },
        ) { _ ->
            renameSymbol(scope, editorTab)
        }

    return listOf(goToDefinition, goToReferences, renameSymbol)
}
