package com.rk.store.ui

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.remember
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
import com.rk.common.PackageManifest
import com.rk.common.PackageType
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.store.StoreRegistry
import com.rk.store.actions.rememberPackageActionContext
import com.rk.utils.errorDialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Confirmation dialog shown when the user opens a package file, regardless of its type. The install itself is delegated
 * to the [com.rk.store.StoreSection] of the detected type.
 */
@Composable
fun XedInstallDialog(manifest: PackageManifest, icon: File?, packageFile: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity
    val scope = rememberCoroutineScope()
    val action = rememberPackageActionContext(context, activity, scope)
    val section = remember(manifest.type) { manifest.type?.let { StoreRegistry.sectionFor(it) } }

    val placeholder =
        when (manifest.type) {
            PackageType.THEME -> drawables.palette
            PackageType.ICON_PACK -> drawables.widgets
            else -> drawables.extension
        }

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
                                .fallback(placeholder)
                                .placeholder(placeholder)
                                .error(placeholder)
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
                            PackageAuthorIcon(manifest.author, Modifier.size(20.dp).padding(end = 4.dp))

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
                description?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (section == null) {
                        errorDialog(activity, msg = strings.unknown_package_format.getString())
                        onDismiss()
                        return@TextButton
                    }

                    // The section owns its own progress and approval UI (loading popup, scan report, errors).
                    scope.launch(Dispatchers.IO) {
                        section.installFromFile(packageFile, action)
                        withContext(Dispatchers.Main) { onDismiss() }
                    }
                }
            ) {
                Text(stringResource(strings.install))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
    )

    // Approval dialogs of the installing section (e.g. the extension security scan report).
    section?.Overlays()
}
