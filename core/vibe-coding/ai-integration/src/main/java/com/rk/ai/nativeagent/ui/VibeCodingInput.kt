@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.models.UIMessagePart
import kotlin.uuid.ExperimentalUuidApi

private data class SlashCommand(
    val id: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val prompt: String,
)

private val slashCommands =
    listOf(
        SlashCommand("fix", "Fix", "Fix bugs or errors", Icons.Outlined.BugReport, "Find and fix issues in the current code"),
        SlashCommand("test", "Test", "Write and run tests", Icons.Outlined.Science, "Write tests for the current code"),
        SlashCommand("refactor", "Refactor", "Improve code structure", Icons.Outlined.Refresh, "Refactor the current code for better quality"),
        SlashCommand("explain", "Explain", "Explain selected code", Icons.Outlined.HelpOutline, "Explain the selected code in detail"),
        SlashCommand("review", "Review", "Code review", Icons.Outlined.RateReview, "Review recent changes for issues"),
        SlashCommand("doc", "Document", "Add documentation", Icons.Outlined.Description, "Add documentation to the selected code"),
        SlashCommand("commit", "Commit", "Stage and commit", Icons.Outlined.Commit, "Stage all changes and create a descriptive commit"),
        SlashCommand("plan", "Plan", "Create execution plan", Icons.Outlined.AccountTree, "Create a step-by-step plan for a complex task"),
    )

private val quickActions =
    listOf(
        SlashCommand("fix", "Fix Bugs", "Find and fix issues", Icons.Outlined.BugReport, "Find and fix issues in the current code"),
        SlashCommand("test", "Add Tests", "Write test cases", Icons.Outlined.Science, "Write tests for the codebase"),
        SlashCommand("refactor", "Refactor", "Improve code quality", Icons.Outlined.Refresh, "Refactor the codebase"),
        SlashCommand("doc", "Document", "Generate documentation", Icons.Outlined.Description, "Add documentation to the code"),
    )

@Composable
fun VibeCodingInput(
    isProcessing: Boolean,
    onSend: (String, List<UIMessagePart>) -> Unit,
    onStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var attachedParts by remember { mutableStateOf<List<UIMessagePart>>(emptyList()) }
    var showSlashMenu by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(true) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bytes = context.contentResolver.openInputStream(it)?.use { stream ->
                    stream.readBytes()
                } ?: return@let
                val fileName = it.lastPathSegment ?: "file"
                val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
                when {
                    mimeType.startsWith("image/") -> {
                        attachedParts = attachedParts + UIMessagePart.Image(url = it.toString())
                    }
                    else -> {
                        val content = String(bytes, Charsets.UTF_8)
                        attachedParts =
                            attachedParts +
                                UIMessagePart.Document(
                                    url = it.toString(),
                                    fileName = fileName,
                                    mime = mimeType,
                                )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    stream.readBytes()
                }
                attachedParts = attachedParts + UIMessagePart.Image(url = it.toString())
            } catch (_: Exception) {}
        }
    }

    fun executeSlashCommand(cmd: SlashCommand) {
        textFieldValue = TextFieldValue("/${cmd.id} ")
        showSlashMenu = false
        showQuickActions = false
    }

    fun send() {
        val text = textFieldValue.text.trim()
        if (text.isNotBlank() || attachedParts.isNotEmpty()) {
            onSend(text, attachedParts)
            textFieldValue = TextFieldValue("")
            attachedParts = emptyList()
            showQuickActions = true
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column {
            // Quick action chips (shown when input is empty and not processing)
            AnimatedVisibility(
                visible = showQuickActions && textFieldValue.text.isBlank() && attachedParts.isEmpty() && !isProcessing,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LazyRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(quickActions) { action ->
                        SuggestionChip(
                            onClick = { executeSlashCommand(action) },
                            label = {
                                Text(action.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            },
                            icon = {
                                Icon(
                                    action.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                )
                            },
                            modifier = Modifier.height(26.dp),
                        )
                    }
                }
            }

            // Attachment chips
            AnimatedVisibility(visible = attachedParts.isNotEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    attachedParts.forEachIndexed { idx, part ->
                        val label =
                            when (part) {
                                is UIMessagePart.Image -> "🖼 Image ${idx + 1}"
                                is UIMessagePart.Text -> "📄 ${part.text.take(30)}..."
                                is UIMessagePart.Document -> "📎 ${part.fileName}"
                                else -> "📎 File ${idx + 1}"
                            }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 1.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colorScheme.primaryContainer.copy(alpha = 0.5f),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier.clickable {
                                            attachedParts = attachedParts.toMutableList().also { it.removeAt(idx) }
                                        }.padding(horizontal = 8.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(12.dp),
                                        tint = colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Slash command suggestions dropdown
            AnimatedVisibility(
                visible = showSlashMenu && textFieldValue.text.startsWith("/"),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                val query = textFieldValue.text.drop(1).substringBefore(" ").lowercase()
                val filtered =
                    if (query.isBlank()) slashCommands
                    else slashCommands.filter { it.id.contains(query, ignoreCase = true) || it.label.contains(query, ignoreCase = true) }

                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                    color = colorScheme.surfaceContainerHigh,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        items(filtered) { cmd: SlashCommand ->
                            Surface(
                                onClick = {
                                    textFieldValue = TextFieldValue("/${cmd.id} ")
                                    showSlashMenu = false
                                },
                                color = Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        cmd.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = colorScheme.primary,
                                    )
                                    Column {
                                        Text(
                                            text = "/${cmd.id}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = cmd.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Main input row
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Attachment buttons
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.size(30.dp),
                        enabled = !isProcessing,
                    ) {
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = "Attach file",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    FilledTonalIconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.size(30.dp),
                        enabled = !isProcessing,
                    ) {
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = "Attach image",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                // Multi-line text input
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val prevText = textFieldValue.text
                        textFieldValue = newValue
                        if (newValue.text.startsWith("/") && !prevText.startsWith("/")) {
                            showSlashMenu = true
                        }
                        if (!newValue.text.startsWith("/")) {
                            showSlashMenu = false
                        }
                        if (newValue.text.isNotEmpty()) {
                            showQuickActions = false
                        }
                        if (newValue.text.isBlank() && attachedParts.isEmpty()) {
                            showQuickActions = true
                        }
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp, max = 160.dp),
                    placeholder = {
                        Text(
                            "Ask or type / for commands...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 6,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Send,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onSend = {
                                if (textFieldValue.text.isNotBlank() || attachedParts.isNotEmpty()) {
                                    send()
                                }
                            },
                        ),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = colorScheme.outlineVariant.copy(alpha = 0.3f),
                            focusedBorderColor = colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedContainerColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            focusedContainerColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                        ),
                    enabled = !isProcessing,
                )

                Spacer(Modifier.width(6.dp))

                // Send / Stop button
                FilledIconButton(
                    onClick = {
                        if (isProcessing) {
                            onStop?.invoke()
                        } else {
                            send()
                        }
                    },
                    modifier = Modifier.size(38.dp),
                    enabled = textFieldValue.text.isNotBlank() || attachedParts.isNotEmpty() || isProcessing,
                    shape = RoundedCornerShape(19.dp),
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor =
                                if (isProcessing) colorScheme.errorContainer
                                else colorScheme.primary,
                            contentColor =
                                if (isProcessing) colorScheme.onErrorContainer
                                else colorScheme.onPrimary,
                        ),
                ) {
                    Icon(
                        imageVector =
                            if (isProcessing) Icons.Outlined.Stop
                            else Icons.Outlined.Send,
                        contentDescription = if (isProcessing) "Stop" else "Send",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Hint text
            if (!isProcessing && textFieldValue.text.isBlank()) {
                Text(
                    text = "Enter to send · / for commands",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp, start = 12.dp),
                )
            }
        }
    }
}
