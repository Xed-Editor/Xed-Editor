package com.rk.extension.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.rk.extension.InstallResult
import com.rk.extension.extensionManager
import com.rk.extension.loader.loadAfterInstall
import com.rk.extension.model.ExtensionManifest
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.extension.ExtensionAuthorIcon
import com.rk.settings.extension.handleInstallResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun XedInstallDialog(manifest: ExtensionManifest, icon: File, packageFile: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.install_from_storage)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model =
                            ImageRequest.Builder(context)
                                .data(icon)
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
                            text = manifest.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExtensionAuthorIcon(manifest.author, Modifier.size(20.dp).padding(end = 4.dp))

                            Text(
                                text = "${manifest.author} • v${manifest.version}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                val description = manifest.description?.ifBlank { null }
                description?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val result = extensionManager.installExtensionFromZip(packageFile)

                        withContext(Dispatchers.Main) {
                            handleInstallResult(result, activity)
                            onDismiss()
                        }

                        if (result is InstallResult.Success) {
                            result.extension.loadAfterInstall(result, activity)
                        }
                    }
                }
            ) {
                Text(stringResource(strings.install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(strings.cancel))
            }
        },
    )
}
