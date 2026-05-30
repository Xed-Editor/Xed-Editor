package com.rk.settings.extension

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.extension.Extension
import com.rk.extension.UpdatableExtension
import com.rk.resources.drawables
import com.rk.theme.Typography

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExtensionCard(
    extension: Extension,
    modifier: Modifier = Modifier,
    installState: InstallState = InstallState.Idle,
    onInstallClick: suspend (Extension) -> Unit,
    onUninstallClick: suspend (Extension) -> Unit,
    onUpdateClick: suspend (UpdatableExtension) -> Unit,
    onClick: (Extension) -> Unit = {},
) {
    val scope = rememberCoroutineScope()

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExtensionAuthorIcon(extension.author, Modifier.size(20.dp).padding(end = 4.dp))
                Text(
                    text = "${extension.author} • v${extension.version}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = Typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val isUpdatable = extension is UpdatableExtension && extension.isUpdatable()
                if (isUpdatable) {
                    Text(
                        text = " → v${extension.newVersion}",
                        style = Typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        endWidget = {
            SmallExtensionActionButton(extension, installState, scope, onInstallClick, onUninstallClick, onUpdateClick)
        },
    )
}
