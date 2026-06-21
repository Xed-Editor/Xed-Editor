package com.rk.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainViewModel
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.theme.DesignTokens
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
    var isExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    suspend fun runHeadless(prompt: String): String {
        val state = viewModel.currentTab
        val wd = if (state is com.rk.tabs.editor.EditorTab) {
            state.projectRoot?.getAbsolutePath()
                ?: state.file?.getAbsolutePath()?.let { java.io.File(it).parent }
        } else null
        val sm = AiProvider.sessionManager ?: return "Error: AI module not available"
        val workingDir = wd ?: "/storage/emulated/0"
        AiProvider.ideBridge?.ensureStarted(viewModel, workingDir)
        AiProvider.ideBridge?.setWorkspacePath(workingDir)
        return sm.runHeadless(prompt, workingDir, viewModel = viewModel)
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = DesignTokens.Elevation.xlarge,
            tonalElevation = DesignTokens.Elevation.none,
            shape = DesignTokens.CornerRadius.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(DesignTokens.Spacing.medium)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Ask ${AiProvider.sessionManager?.currentAgent?.displayName ?: "AI"}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            response = null
                            onDismiss()
                        },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(DesignTokens.Spacing.small))

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
                        shape = MaterialTheme.shapes.medium,
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )

                    Spacer(Modifier.width(DesignTokens.Spacing.small))

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
                        modifier = Modifier.size(38.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                response?.let { text ->
                    Spacer(Modifier.height(DesignTokens.Spacing.small))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().heightIn(max = if (isExpanded) 400.dp else 150.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(DesignTokens.Spacing.small)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
