package com.rk.settings.extension

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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.rk.extension.extensionManager
import com.rk.extension.model.ExtensionId
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.store.PackageActionContext
import com.rk.store.actions.rememberPackageInstallState
import com.rk.store.ui.SmallPackageActionButton

/**
 * Prompt listing the dependencies that have to be installed before an extension can be installed.
 *
 * Confirming hands control back to the install flow, which installs the dependencies one after another.
 */
@Composable
fun DependenciesDialog(
    extensionIds: List<ExtensionId>,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.missing_dependencies)) },
        text = {
            Column {
                Text(
                    text = stringResource(strings.dependencies_description),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(12.dp))

                extensionIds.forEach { extensionId -> DependencyItem(extensionId = extensionId, action = null) }
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(strings.continue_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
    )
}

/** Informational list of extensions recommended by an installed extension, with install/update actions. */
@Composable
fun RecommendationsDialog(
    extensionIds: List<ExtensionId>,
    action: PackageActionContext,
    onInstallClick: (Extension) -> Unit,
    onUpdateClick: (Extension) -> Unit,
    onUninstallClick: (Extension) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.recommendations)) },
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
                        action = action,
                        onInstallClick = onInstallClick,
                        onUpdateClick = onUpdateClick,
                        onUninstallClick = onUninstallClick,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.close)) } },
    )
}

@Composable
private fun DependencyItem(
    extensionId: ExtensionId,
    action: PackageActionContext?,
    onInstallClick: (Extension) -> Unit = {},
    onUpdateClick: (Extension) -> Unit = {},
    onUninstallClick: (Extension) -> Unit = {},
) {
    val extension = extensionManager.getExtension(extensionId)
    val scope = rememberCoroutineScope()

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

        if (action != null && extension != null) {
            SmallPackageActionButton(
                installState = rememberPackageInstallState(extension),
                scope = scope,
                onInstallClick = { onInstallClick(extension) },
                onUninstallClick = { onUninstallClick(extension) },
                onUpdateClick = { onUpdateClick(extension) },
            )
        }
    }
}
