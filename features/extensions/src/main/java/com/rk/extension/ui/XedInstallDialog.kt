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
import com.rk.activities.main.MainViewModel
import com.rk.crashhandler.CrashActivity
import com.rk.extension.InstallResult
import com.rk.extension.extensionManager
import com.rk.extension.loader.LoadScenario
import com.rk.extension.loader.load
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.extension.ExtensionAuthorIcon
import com.rk.settings.extension.handleInstallResult
import com.rk.utils.application
import com.rk.utils.errorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun XedInstallDialog(viewModel: MainViewModel) {
    val manifest = viewModel.pendingExtensionManifest ?: return

    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { viewModel.closeExtensionIntentDialog() },
        title = { Text(stringResource(strings.install_from_storage)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model =
                            ImageRequest.Builder(context)
                                .data(viewModel.pendingExtensionIcon)
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
                    val file = viewModel.pendingExtensionFile
                    if (file != null) {
                        scope.launch(Dispatchers.IO) {
                            val result = extensionManager.installExtensionFromZip(file)

                            // TODO: Duplicate code
                            if (result is InstallResult.Success) {
                                extensionManager.setExtensionCrashed(result.extension, false)
                                val loadScenario =
                                    if (result.performedUpdate) LoadScenario.UPDATE else LoadScenario.INSTALL
                                result.extension.load(application!!, loadScenario).onFailure { error ->
                                    extensionManager.setExtensionCrashed(result.extension, true)
                                    withContext(Dispatchers.Main) {
                                        activity?.let {
                                            CrashActivity.start(
                                                context = it,
                                                extensionId = result.extension.id,
                                                extensionName = result.extension.name,
                                                extensionVersion = result.extension.version,
                                                extensionAuthor = result.extension.author.toString(),
                                                repository = result.extension.repository,
                                                error = error,
                                            )
                                        }
                                            ?: run {
                                                errorDialog(
                                                    activity,
                                                    msg = error.message ?: strings.unknown_error.getString(),
                                                )
                                            }
                                    }
                                }
                            }

                            withContext(Dispatchers.Main) {
                                handleInstallResult(result, activity)
                                viewModel.closeExtensionIntentDialog()
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(strings.install))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.closeExtensionIntentDialog()
                }
            ) {
                Text(stringResource(strings.cancel))
            }
        },
    )
}
