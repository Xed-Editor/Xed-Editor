package com.rk.settings.extension

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rk.App
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.extension.Extension
import com.rk.extension.UpdatableExtension
import com.rk.extension.extensionManager
import com.rk.extension.manager.ExtensionRegistry
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.theme.Typography

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExtensionCard(
    extension: Extension,
    modifier: Modifier = Modifier,
    installState: InstallState = InstallState.Idle,
    onInstallClick: suspend () -> Unit,
    onUninstallClick: suspend () -> Unit,
    onUpdateClick: suspend () -> Unit,
    onClick: (Extension) -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    val minAppVersion = extension.minAppVersion
    val maxAppVersion = extension.maxAppVersion

    val xedVersionCode = App.versionCode
    val outdatedClient = minAppVersion != null && xedVersionCode < minAppVersion
    val outdatedExtension = maxAppVersion != null && xedVersionCode > maxAppVersion

    PreferenceTemplate(
        modifier = modifier.fillMaxWidth().clickable(onClick = { onClick(extension) }),
        startWidget = {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalContext.current)
                        .data(extension.iconUrl)
                        .placeholder(drawables.extension)
                        .error(drawables.extension)
                        .crossfade(true)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                contentDescription = null,
            )
        },
        title = {
            Text(text = extension.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        description = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExtensionAuthorIcon(extension.author, Modifier.size(20.dp).padding(end = 4.dp))
                    Text(
                        text = "${extension.author} • v${extension.version}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = Typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val isUpdatable = extension is UpdatableExtension && extension.hasUpdate()
                    if (isUpdatable) {
                        Text(
                            text = " → v${extension.newVersion}",
                            style = Typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (extensionManager.isExtensionCrashed(extension.id)) {
                        Text(
                            text = " • ${stringResource(strings.disabled_crashed)}",
                            style = Typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                val progress = ExtensionRegistry.downloadProgress[extension.id]
                if (progress != null) {
                    Spacer(Modifier.height(4.dp))
                    if (progress >= 0f) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        endWidget = {
            SmallExtensionActionButton(
                installState = installState,
                scope = scope,
                onInstallClick = onInstallClick,
                onUninstallClick = onUninstallClick,
                onUpdateClick = onUpdateClick,
                outdatedWarning = outdatedClient || outdatedExtension,
            )
        },
    )
}
