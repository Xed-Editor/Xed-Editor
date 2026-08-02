package com.rk.settings.extension

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rk.extension.Extension
import com.rk.extension.model.ExtensionId
import com.rk.extension.extensionManager
import com.rk.resources.drawables
import com.rk.resources.strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun RecommendationsDialog(
    extensionIds: List<ExtensionId>,
    scope: CoroutineScope,
    activity: AppCompatActivity?,
    onInstallClick: (Extension) -> Unit,
    onUpdateClick: (Extension) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(strings.recommendations))
        },
        text = {
            Column {
                Text(
                    text = stringResource(strings.recommendations_description),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(12.dp))

                extensionIds.forEach { extensionId ->
                    DependencyItem(
                        extensionId = extensionId,
                        showActions = true,
                        onInstallClick = onInstallClick,
                        onUpdateClick = onUpdateClick,
                        onUninstallClick = { dependency ->
                            runExtensionUninstallAction(
                                extension = dependency,
                                updateInstallState = {},
                                scope = scope,
                                activity = activity,
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(strings.close))
            }
        },
    )
}

@Composable
fun DependenciesDialog(
    extensionIds: List<ExtensionId>,
    scope: CoroutineScope,
    activity: AppCompatActivity?,
    onDismiss: () -> Unit,
    onCompletion: () -> Unit,
) {
    val context = LocalContext.current
    var isInstalling by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isInstalling) onDismiss() },
        title = {
            Text(stringResource(strings.missing_dependencies))
        },
        text = {
            Column {
                Text(
                    text = stringResource(strings.dependencies_description),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(12.dp))

                extensionIds.forEach { extensionId ->
                    DependencyItem(
                        extensionId = extensionId,
                        showActions = false,
                        onInstallClick = {},
                        onUpdateClick = {},
                        onUninstallClick = {},
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isInstalling,
                onClick = {
                    scope.launch {
                        isInstalling = true
                        val success = batchInstallExtensions(extensionIds, context, activity)
                        isInstalling = false
                        if (success) {
                            onCompletion()
                        }
                    }
                },
            ) {
                Text(stringResource(if (isInstalling) strings.installing else strings.continue_action))
            }
        },
        dismissButton = {
            TextButton(enabled = !isInstalling, onClick = onDismiss) {
                Text(stringResource(strings.cancel))
            }
        },
    )
}

@Composable
private fun DependencyItem(
    extensionId: ExtensionId,
    showActions: Boolean,
    onInstallClick: (Extension) -> Unit,
    onUpdateClick: (Extension) -> Unit,
    onUninstallClick: (Extension) -> Unit,
) {
    val extension = extensionManager.getExtension(extensionId)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model =
                ImageRequest.Builder(LocalContext.current)
                    .data(extension?.iconUrl)
                    .fallback(drawables.extension)
                    .placeholder(drawables.extension)
                    .error(drawables.extension)
                    .crossfade(true)
                    .build(),
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
            contentDescription = null,
        )

        Spacer(Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = extension?.name ?: extensionId,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            extension?.version?.let {
                Text(
                    text = "v$it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        extension?.let {
            if (showActions) {
                val installState = rememberInstallState(extension)

                SmallExtensionActionButton(
                    installState = installState,
                    scope = rememberCoroutineScope(),
                    onInstallClick = {
                        onInstallClick(it)
                    },
                    onUninstallClick = {
                        onUninstallClick(it)
                    },
                    onUpdateClick = {
                        onUpdateClick(it)
                    },
                )
            }
        }
    }
}
