package com.rk.store.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rk.App
import com.rk.common.PackageType
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.packages.InstallState
import com.rk.packages.StoreManager
import com.rk.packages.model.Package
import com.rk.packages.model.UpdatablePackage
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.store.StoreRegistry
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

    val outdatedClient = pkg.isOutdatedClient()

    PreferenceTemplate(
        modifier = modifier.fillMaxWidth().clickable(onClick = { onClick(pkg) }),
        startWidget = { PackageIcon(iconUrl = pkg.iconUrl, type = pkg.type, size = 48.dp) },
        title = { Text(text = pkg.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        description = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PackageAuthorIcon(pkg.author, Modifier.size(20.dp).padding(end = 4.dp))
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

                    StoreRegistry.sectionFor(pkg.type)?.StatusBadge(pkg)
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
            SmallPackageActionButton(
                installState = installState,
                scope = scope,
                onInstallClick = onInstallClick,
                onUninstallClick = onUninstallClick,
                onUpdateClick = onUpdateClick,
                outdatedWarning = outdatedClient || !pkg.isSupportedArchitecture(),
            )
        },
    )
}

/** Icon of a package, falling back to the placeholder of its [type]. */
@Composable
fun PackageIcon(iconUrl: String, type: PackageType, size: Dp) {
    val placeholder =
        when (type) {
            PackageType.THEME -> drawables.palette
            PackageType.ICON_PACK -> drawables.widgets
            else -> drawables.extension
        }

    AsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(iconUrl)
                .placeholder(placeholder)
                .error(placeholder)
                .crossfade(true)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build(),
        modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
        contentDescription = null,
    )
}

/** Whether the installed app is too old for [this] package. */
fun Package.isOutdatedClient(): Boolean {
    val minAppVersion = minAppVersion ?: return false
    return App.versionCode < minAppVersion
}

/** Whether the device CPU is supported by [this] package. */
fun Package.isSupportedArchitecture(): Boolean {
    val currentArchitecture = Build.SUPPORTED_ABIS.firstOrNull() ?: return true
    return supportedArchitectures?.contains(currentArchitecture) ?: true
}

@Composable
fun PackageUnavailableWarning(pkg: Package) {
    if (!pkg.isOutdatedClient() && pkg.isSupportedArchitecture()) return

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = stringResource(strings.warning),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            stringResource(if (pkg.isOutdatedClient()) strings.outdated_client else strings.unsupported_architecture),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
