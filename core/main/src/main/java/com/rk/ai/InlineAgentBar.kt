package com.rk.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.rk.icons.XedIcon
import com.rk.resources.drawables
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
        val state = viewModel.currentTab
        val wd = if (state is com.rk.tabs.editor.EditorTab) {
            state.projectRoot?.getAbsolutePath()
                ?: state.file?.getAbsolutePath()?.let { java.io.File(it).parent }
        } else null
        return AiSessionManager.runHeadless(prompt, wd ?: "/storage/emulated/0")
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
                    if (response != null) {
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { response = null }) {
                            XedIcon(com.rk.icons.Icon.DrawableRes(drawables.close), contentDescription = "Clear Response", modifier = Modifier.size(20.dp))
                        }
                    }
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
