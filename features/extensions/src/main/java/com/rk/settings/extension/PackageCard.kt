package com.rk.settings.extension

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
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
import com.rk.common.PackageType
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.extension.Extension
import com.rk.extension.InstallState
import com.rk.extension.extensionManager
import com.rk.extension.manager.StoreManager
import com.rk.extension.model.Package
import com.rk.extension.model.UpdatablePackage
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.theme.Typography

@Composable
fun PackageCard(
    pkg: Package,
    modifier: Modifier = Modifier,
    installState: InstallState = InstallState.Idle,
    onInstallClick: suspend () -> Unit,
    onUninstallClick: suspend () -> Unit,
    onUpdateClick: suspend () -> Unit,
    onClick: (Package) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val downloadProgress by StoreManager.downloadProgress.collectAsStateWithLifecycle()

    val xedVersionCode = App.versionCode
    val minAppVersion = pkg.minAppVersion
    val outdatedClient = minAppVersion != null && xedVersionCode < minAppVersion

    val currentArchitecture = Build.SUPPORTED_ABIS.firstOrNull()
    val supportedArchitecture =
        currentArchitecture == null || pkg.supportedArchitectures?.contains(currentArchitecture) ?: true

    val placeholder =
        when (pkg.type) {
            PackageType.THEME -> drawables.palette
            PackageType.ICON_PACK -> drawables.widgets
            else -> drawables.extension
        }

    PreferenceTemplate(
        modifier = modifier.fillMaxWidth().clickable(onClick = { onClick(pkg) }),
        startWidget = {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalContext.current)
                        .data(pkg.iconUrl)
                        .placeholder(placeholder)
                        .error(placeholder)
                        .crossfade(true)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                contentDescription = null,
            )
        },
        title = {
            Text(text = pkg.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        description = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExtensionAuthorIcon(pkg.author, Modifier.size(20.dp).padding(end = 4.dp))
                    Text(
                        text = "${pkg.author} • v${pkg.version}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = Typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val isUpdatable = pkg is UpdatablePackage && pkg.hasUpdate()
                    if (isUpdatable) {
                        Text(
                            text = " → v${pkg.newVersion}",
                            style = Typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (pkg is Extension && extensionManager.isExtensionCrashed(pkg)) {
                        Text(
                            text = " • ${stringResource(strings.disabled_crashed)}",
                            style = Typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                val progress = downloadProgress[pkg.id]
                if (progress != null) {
                    Spacer(Modifier.height(4.dp))
                    if (progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        LinearProgressIndicator(
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
                outdatedWarning = outdatedClient || !supportedArchitecture,
            )
        },
    )
}
