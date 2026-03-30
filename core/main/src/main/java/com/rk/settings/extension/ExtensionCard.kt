package com.rk.settings.extension

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.surfaceColorAtElevation
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
import com.rk.extension.Extension
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
    onClick: (Extension) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val cardColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)

    Card(
        modifier =
            modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { onClick(extension) }),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = contentColorFor(cardColor)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalContext.current)
                        .data(extension.iconUrl)
                        .fallback(drawables.extension)
                        .crossfade(true)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).padding(end = 16.dp),
                contentDescription = null,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name,
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExtensionAuthorIcon(extension.author, Modifier.size(16.dp).padding(end = 4.dp))
                    Text(
                        text = "${extension.author} • v${extension.version}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = Typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ExtensionActionButton(extension, installState, scope, onInstallClick, onUninstallClick)
        }
    }
}
