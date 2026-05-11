package com.rk.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainViewModel
import com.rk.ai.session.AiSessionManager
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun InlineAgentBar(
    viewModel: MainViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var response by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    suspend fun runHeadless(prompt: String): String {
        val bridge = IdeBridge.getBridgeInfo()
        val tab = viewModel.currentTab as? EditorTab
        val wd = tab?.projectRoot?.getAbsolutePath()
            ?: tab?.file?.getAbsolutePath()?.let { java.io.File(it).parent }
            ?: "/storage/emulated/0"

        IdeBridge.ensureStarted(viewModel, wd)
        val result = GeminiCli.agent(
            prompt = prompt,
            workingDir = wd,
            ideBridge = bridge,
            timeoutSeconds = 60,
        )
        return GeminiCli.stripCodeFences(result.output)
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Ask ${AiSessionManager.currentAgent.displayName}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        response = null
                        onDismiss()
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Ask about code...", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = {
                            if (input.isBlank()) return@FilledTonalIconButton
                            isLoading = true
                            val prompt = input.trim()
                            input = ""
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val result = runHeadless(prompt)
                                    withContext(Dispatchers.Main) { response = result }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { response = "Error: ${e.message}" }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = input.isNotBlank() && !isLoading,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Send, contentDescription = "Send")
                        }
                    }
                }

                response?.let { text ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    ) {
                        Column(modifier = Modifier.padding(10.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
