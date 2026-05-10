package com.rk.tabs.editor

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.blankj.utilcode.util.ClipboardUtils
import com.rk.ai.GeminiCli
import com.rk.exec.TerminalCommand
import com.rk.exec.launchTerminal
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class GeminiActionMode {
    Ask,
    Apply,
    Insert,
    Agent,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorTab.GeminiAssistantSheet() {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current

    fun currentEditor() = editorState.editor.get()

    fun selectedOrFileText(): String {
        val editor = currentEditor() ?: return editorState.content?.toString().orEmpty()
        return if (editor.isTextSelected) {
            editor.text.substring(editor.cursorRange.startIndex, editor.cursorRange.endIndex)
        } else {
            editor.text.toString()
        }
    }

    fun currentProjectDir(): String = projectRoot?.getAbsolutePath() ?: file.getAbsolutePath()

    fun runGemini(prompt: String, mode: GeminiActionMode, applyResult: ((String) -> Unit)? = null) {
        if (prompt.isBlank() || editorState.geminiRunning) return

        editorState.geminiRunning = true
        editorState.geminiOutput = ""
        editorState.geminiOutput = "Starting Gemini ${mode.name.lowercase()} for ${file.getName()}..."

        scope.launch(Dispatchers.IO) {
            val workingDir = GeminiCli.workingDirFor(file, projectRoot)
            val result =
                runCatching {
                    if (mode == GeminiActionMode.Agent) {
                        GeminiCli.agent(prompt = prompt, workingDir = workingDir, projectDir = workingDir)
                    } else {
                        GeminiCli.prompt(prompt = prompt, workingDir = workingDir, projectDir = workingDir)
                    }
                }

            withContext(Dispatchers.Main) {
                editorState.geminiRunning = false
                result
                    .onSuccess { shellResult ->
                        val output = shellResult.output.ifBlank { shellResult.error }
                        val header =
                            if (shellResult.exitCode == 0) {
                                "Gemini finished successfully."
                            } else {
                                "Gemini failed with exit code ${shellResult.exitCode}."
                            }
                        editorState.geminiOutput = "$header\n\n$output".trim()
                        if (shellResult.exitCode == 0 && applyResult != null) {
                            applyResult(GeminiCli.stripCodeFences(output))
                        }
                    }
                    .onFailure { throwable ->
                        editorState.geminiOutput = throwable.message ?: throwable.toString()
                    }
            }
        }
    }

    fun openFullCli() {
        val currentActivity = activity ?: return
        val workingDir = currentProjectDir()
        launchTerminal(
            currentActivity,
            TerminalCommand(
                exe = "/bin/bash",
                args =
                    arrayOf(
                        localBinDir().child("gemini-cli").absolutePath,
                        "--skip-trust",
                        "--include-directories",
                        workingDir,
                    ),
                id = "gemini-cli-project",
                terminatePreviousSession = false,
                workingDir = workingDir,
            ),
        )
    }

    ModalBottomSheet(onDismissRequest = { editorState.showGeminiAssistant = false }) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = strings.gemini_assistant.getString(), style = MaterialTheme.typography.titleLarge)
            Text(text = "Project: ${currentProjectDir()}", style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = editorState.geminiPrompt,
                onValueChange = { editorState.geminiPrompt = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                label = { Text(strings.gemini_prompt.getString()) },
                minLines = 3,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { editorState.geminiPrompt = "Explain this code and point out important behavior." }) {
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

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank(),
                    onClick = {
                        val editor = currentEditor()
                        val contextText = selectedOrFileText()
                        runGemini(
                            prompt =
                                """
                                You are an AI coding assistant inside Xed-Editor.
                                User request: ${editorState.geminiPrompt}

                                Project root: ${currentProjectDir()}
                                File: ${file.getAbsolutePath()}
                                Current ${if (editor?.isTextSelected == true) "selection" else "file"}:
                                ```
                                $contextText
                                ```

                                Answer concisely. Do not edit files.
                                """
                                    .trimIndent(),
                            mode = GeminiActionMode.Ask,
                        )
                    },
                ) {
                    Text(strings.ask.getString())
                }

                Button(
                    enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank() && currentEditor() != null,
                    onClick = {
                        val editor = currentEditor() ?: return@Button
                        val hasSelection = editor.isTextSelected
                        val contextText = selectedOrFileText()
                        val start = if (hasSelection) editor.cursorRange.startIndex else 0
                        val end = if (hasSelection) editor.cursorRange.endIndex else editor.text.toString().length
                        runGemini(
                            prompt =
                                """
                                Rewrite the ${if (hasSelection) "selected code" else "entire file"} for this request: ${editorState.geminiPrompt}

                                Return ONLY the replacement code/text. No markdown, no explanation.

                                Project root: ${currentProjectDir()}
                                File: ${file.getAbsolutePath()}
                                Input:
                                ```
                                $contextText
                                ```
                                """
                                    .trimIndent(),
                            mode = GeminiActionMode.Apply,
                            applyResult = { replacement -> editor.text.replace(start, end, replacement) },
                        )
                    },
                ) {
                    Text(strings.apply.getString())
                }

                TextButton(
                    enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank() && currentEditor() != null,
                    onClick = {
                        val editor = currentEditor() ?: return@TextButton
                        val contextText = selectedOrFileText()
                        runGemini(
                            prompt =
                                """
                                Generate code/text for this request: ${editorState.geminiPrompt}

                                Return ONLY the code/text to insert. No markdown, no explanation.

                                Project root: ${currentProjectDir()}
                                Nearby context from ${file.getName()}:
                                ```
                                $contextText
                                ```
                                """
                                    .trimIndent(),
                            mode = GeminiActionMode.Insert,
                            applyResult = { insertion ->
                                editor.text.insert(editor.cursor.leftLine, editor.cursor.leftColumn, insertion)
                            },
                        )
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
                                Act as a Gemini CLI coding agent for this project.
                                User request: ${editorState.geminiPrompt}

                                Use the full codebase under the project root. You may inspect files, search, and edit project files as needed.
                                After changes, summarize exactly what changed and list modified files.

                                Project root: ${currentProjectDir()}
                                Current file: ${file.getAbsolutePath()}
                                Current editor context:
                                ```
                                ${selectedOrFileText()}
                                ```
                                """
                                    .trimIndent(),
                            mode = GeminiActionMode.Agent,
                            applyResult = { _ ->
                                viewModel.tabs.filterIsInstance<EditorTab>().forEach { tab -> tab.refresh() }
                            },
                        )
                    },
                ) {
                    Text("Agent")
                }

                TextButton(enabled = activity != null, onClick = { openFullCli() }) { Text("CLI") }

                TextButton(
                    enabled = editorState.geminiOutput.isNotBlank(),
                    onClick = {
                        ClipboardUtils.copyText("Gemini log", editorState.geminiOutput)
                        toast(strings.copied)
                    },
                ) {
                    Text("Copy log")
                }
            }

            if (editorState.geminiRunning) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(strings.wait.getString())
                }
            }

            if (editorState.geminiOutput.isNotBlank()) {
                Text(
                    text = editorState.geminiOutput,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
