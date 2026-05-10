package com.rk.tabs.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rk.ai.GeminiCli
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTab.GeminiAssistantSheet() {
    val scope = rememberCoroutineScope()
    val editor = editorState.editor.get()

    fun selectedOrFileText(): String {
        val currentEditor = editor ?: return editorState.content?.toString().orEmpty()
        return if (currentEditor.isTextSelected) {
            currentEditor.text.substring(currentEditor.cursorRange.startIndex, currentEditor.cursorRange.endIndex)
        } else {
            currentEditor.text.toString()
        }
    }

    fun runGemini(prompt: String, applyResult: ((String) -> Unit)? = null, agentMode: Boolean = false) {
        if (prompt.isBlank() || editorState.geminiRunning) return
        editorState.geminiRunning = true
        editorState.geminiOutput = ""
        scope.launch(Dispatchers.IO) {
            runCatching {
                    if (agentMode) {
                        GeminiCli.agent(prompt, GeminiCli.workingDirFor(file))
                    } else {
                        GeminiCli.prompt(prompt, GeminiCli.workingDirFor(file))
                    }
                }
                .onSuccess { result ->
                    val output = result.output.ifBlank { result.error }
                    withContext(Dispatchers.Main) {
                        editorState.geminiRunning = false
                        editorState.geminiOutput = output
                        if (result.exitCode == 0 && applyResult != null) {
                            applyResult(GeminiCli.stripCodeFences(output))
                        }
                    }
                }
                .onFailure { throwable ->
                    withContext(Dispatchers.Main) {
                        editorState.geminiRunning = false
                        editorState.geminiOutput = throwable.message ?: throwable.toString()
                    }
                }
        }
    }

    ModalBottomSheet(onDismissRequest = { editorState.showGeminiAssistant = false }) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = strings.gemini_assistant.getString(), style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = editorState.geminiPrompt,
                onValueChange = { editorState.geminiPrompt = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                label = { Text(strings.gemini_prompt.getString()) },
                minLines = 3,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { editorState.geminiPrompt = "Explain this code and point out important behavior." }
                ) {
                    Text("Explain")
                }
                TextButton(onClick = { editorState.geminiPrompt = "Find bugs and suggest a safe fix." }) {
                    Text("Find bugs")
                }
                TextButton(
                    onClick = {
                        editorState.geminiPrompt = "Refactor this code to be cleaner without changing behavior."
                    }
                ) {
                    Text("Refactor")
                }
                TextButton(onClick = { editorState.geminiPrompt = "Generate or improve tests for this code." }) {
                    Text("Tests")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank(),
                    onClick = {
                        val contextText = selectedOrFileText()
                        runGemini(
                            """
                            You are an AI coding assistant inside Xed-Editor.
                            User request: ${editorState.geminiPrompt}

                            File: ${file.getAbsolutePath()}
                            Current ${if (editor?.isTextSelected == true) "selection" else "file"}:
                            ```
                            $contextText
                            ```

                            Answer concisely. Do not edit files.
                            """
                                .trimIndent()
                        )
                    },
                ) {
                    Text(strings.ask.getString())
                }

                Button(
                    enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank() && editor != null,
                    onClick = {
                        val currentEditor = editor ?: return@Button
                        val hasSelection = currentEditor.isTextSelected
                        val contextText = selectedOrFileText()
                        val start = if (hasSelection) currentEditor.cursorRange.startIndex else 0
                        val end =
                            if (hasSelection) currentEditor.cursorRange.endIndex
                            else currentEditor.text.toString().length
                        runGemini(
                            """
                            Rewrite the ${if (hasSelection) "selected code" else "entire file"} for this request: ${editorState.geminiPrompt}

                            Return ONLY the replacement code/text. No markdown, no explanation.

                            File: ${file.getAbsolutePath()}
                            Input:
                            ```
                            $contextText
                            ```
                            """
                                .trimIndent()
                        ) { replacement ->
                            currentEditor.text.replace(start, end, replacement)
                        }
                    },
                ) {
                    Text(strings.apply.getString())
                }

                TextButton(
                    enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank() && editor != null,
                    onClick = {
                        val currentEditor = editor ?: return@TextButton
                        val contextText = selectedOrFileText()
                        runGemini(
                            """
                            Generate code/text for this request: ${editorState.geminiPrompt}

                            Return ONLY the code/text to insert. No markdown, no explanation.

                            Nearby context from ${file.getName()}:
                            ```
                            $contextText
                            ```
                            """
                                .trimIndent()
                        ) { insertion ->
                            currentEditor.text.insert(
                                currentEditor.cursor.leftLine,
                                currentEditor.cursor.leftColumn,
                                insertion,
                            )
                        }
                    },
                ) {
                    Text(strings.insert.getString())
                }

                TextButton(
                    enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank(),
                    onClick = {
                        runGemini(
                            prompt =
                                """
                            Act as a coding agent for this project.
                            User request: ${editorState.geminiPrompt}

                            You may inspect and edit files in the current project directory if needed.
                            After changes, summarize exactly what changed.

                            Current file: ${file.getAbsolutePath()}
                            Current editor context:
                            ```
                            ${selectedOrFileText()}
                            ```
                            """
                                    .trimIndent(),
                            applyResult = { _ -> refresh() },
                            agentMode = true,
                        )
                    },
                ) {
                    Text("Agent")
                }
            }

            if (editorState.geminiRunning) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(strings.wait.getString())
                }
            }

            if (editorState.geminiOutput.isNotBlank()) {
                Text(
                    text = editorState.geminiOutput,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
