package com.rk.ai.git

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.git.ChangeType
import com.rk.git.GitChange
import com.rk.git.GitViewModel
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.utils.getGitColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ConflictGroup(
    conflicts: List<GitChange>,
    expanded: Boolean,
    gitViewModel: GitViewModel,
    onToggle: () -> Unit,
) {
    if (conflicts.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme
    val selectionState = when {
        conflicts.all { it.isChecked } -> ToggleableState.On
        conflicts.none { it.isChecked } -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(drawables.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp).rotate(if (expanded) 90f else 0f),
            )
            Spacer(Modifier.width(3.dp))
            TriStateCheckbox(
                enabled = !gitViewModel.isLoading,
                state = selectionState,
                onClick = {
                    if (selectionState == ToggleableState.On) {
                        conflicts.forEach { gitViewModel.removeChange(it) }
                    } else {
                        conflicts.forEach { gitViewModel.addChange(it) }
                    }
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(strings.conflicts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        AnimatedVisibility(visible = expanded) {
            ChangesItemList(conflicts, gitViewModel, colorScheme)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun ChangeGroup(
    label: String,
    items: List<GitChange>,
    expanded: Boolean,
    gitViewModel: GitViewModel,
    isStaged: Boolean = false,
    onToggle: () -> Unit,
) {
    if (items.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme
    val selectionState = when {
        items.all { it.isChecked } -> ToggleableState.On
        items.none { it.isChecked } -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(drawables.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp).rotate(if (expanded) 90f else 0f),
            )
            Spacer(Modifier.width(3.dp))
            TriStateCheckbox(
                enabled = !gitViewModel.isLoading,
                state = selectionState,
                onClick = {
                    if (selectionState == ToggleableState.On) {
                        items.forEach { gitViewModel.removeChange(it) }
                    } else {
                        items.forEach { gitViewModel.addChange(it) }
                    }
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isStaged) colorScheme.primary else colorScheme.onSurface,
            )
        }
        AnimatedVisibility(visible = expanded) {
            ChangesItemList(items, gitViewModel, colorScheme, isStaged = isStaged)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun ChangesItemList(
    items: List<GitChange>,
    gitViewModel: GitViewModel,
    colorScheme: ColorScheme,
    isStaged: Boolean = false
) {
    var showDiscardConfirmDialog by remember { mutableStateOf<GitChange?>(null) }

    if (showDiscardConfirmDialog != null) {
        val changeToDiscard = showDiscardConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = null },
            title = { Text(stringResource(strings.discard) + "?") },
            text = { Text("Are you sure you want to discard all uncommitted changes in ${changeToDiscard.path.substringAfterLast("/")}? This operation cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        gitViewModel.discardChange(changeToDiscard)
                        showDiscardConfirmDialog = null
                    }
                ) {
                    Text(stringResource(strings.discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmDialog = null }) {
                    Text(stringResource(strings.cancel))
                }
            }
        )
    }

    Column(modifier = Modifier.padding(start = 36.dp, end = 8.dp)) {
        items.forEach { change ->
            var expanded by remember { mutableStateOf(false) }
            var diffText by remember { mutableStateOf<String?>(null) }
            var isDiffLoading by remember { mutableStateOf(false) }

            LaunchedEffect(expanded) {
                if (expanded && diffText == null) {
                    isDiffLoading = true
                    diffText = withContext(Dispatchers.IO) {
                        gitViewModel.getDiffForFile(change.path)
                    }
                    isDiffLoading = false
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isStaged) {
                            Modifier.background(
                                color = colorScheme.primaryContainer.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(4.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Checkbox(
                        enabled = !gitViewModel.isLoading,
                        checked = change.isChecked,
                        onCheckedChange = { gitViewModel.toggleChange(change) },
                        modifier = Modifier.size(20.dp),
                    )

                    val fileName = change.path.substringAfterLast("/")
                    val gitColor = getGitColor(change.type)

                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = gitColor ?: colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    ChangeTypeBadge(changeType = change.type, colorScheme = colorScheme)

                    Spacer(Modifier.width(4.dp))

                    Text(
                        text = change.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )

                    if (!isStaged) {
                        IconButton(
                            onClick = { showDiscardConfirmDialog = change },
                            enabled = !gitViewModel.isLoading,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                painter = painterResource(drawables.undo),
                                contentDescription = "Discard changes",
                                tint = colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Icon(
                        painter = painterResource(drawables.chevron_right),
                        contentDescription = "Expand diff",
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(if (expanded) 90f else 0f)
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .heightIn(max = 200.dp),
                        shape = MaterialTheme.shapes.small,
                        color = colorScheme.surfaceContainerLow,
                        border = BorderStroke(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        if (isDiffLoading) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        } else if (diffText.isNullOrBlank()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No diff details available.", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .verticalScroll(scrollState)
                            ) {
                                diffText!!.split("\n").forEach { line ->
                                    val color = when {
                                        line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF2E7D32)
                                        line.startsWith("-") && !line.startsWith("---") -> Color(0xFFC62828)
                                        line.startsWith("@@") -> colorScheme.primary
                                        line.startsWith("diff") || line.startsWith("index") -> colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        else -> colorScheme.onSurface
                                    }
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = color
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChangeTypeBadge(changeType: ChangeType, colorScheme: ColorScheme) {
    val (label, bgColor, textColor) = when (changeType) {
        ChangeType.ADDED -> Triple("A", colorScheme.primaryContainer.copy(alpha = 0.6f), colorScheme.onPrimaryContainer)
        ChangeType.MODIFIED -> Triple("M", colorScheme.tertiaryContainer.copy(alpha = 0.6f), colorScheme.onTertiaryContainer)
        ChangeType.DELETED -> Triple("D", colorScheme.errorContainer.copy(alpha = 0.4f), colorScheme.onErrorContainer)
        ChangeType.CONFLICTING -> Triple("!", colorScheme.errorContainer, colorScheme.onErrorContainer)
        ChangeType.UNTRACKED -> Triple("U", colorScheme.surfaceVariant.copy(alpha = 0.6f), colorScheme.onSurfaceVariant)
    }
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = bgColor,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}
