package com.rk.settings.extension

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rk.App
import com.rk.components.compose.preferences.base.RefreshablePreferenceLayout
import com.rk.extension.manager.StoreManager
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.theme.Typography

@Composable
fun IconPackDetail(iconPackId: String?) {
    val iconPackEntry = iconPackId?.let { App.iconPackManager.storeIconPacks[it] }

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    RefreshablePreferenceLayout(
        label = iconPackEntry?.manifest?.name ?: stringResource(strings.icon_pack_not_found),
        backArrowVisible = true,
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            refreshKey++
        },
    ) {
        if (iconPackEntry == null) {
            Text(stringResource(strings.icon_pack_not_found), modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model =
                            ImageRequest.Builder(LocalContext.current)
                                .data(StoreManager.getIconPackIconUrl(iconPackEntry.id))
                                .placeholder(drawables.widgets)
                                .error(drawables.widgets)
                                .crossfade(true)
                                .build(),
                        contentDescription = null,
                        modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(
                            iconPackEntry.manifest.name,
                            style = Typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(iconPackEntry.id, style = Typography.labelMedium)
                    }
                }

                MarkdownViewer(
                    url = StoreManager.getIconPackReadmeUrl(iconPackEntry.id),
                    refreshKey = refreshKey,
                    onLoaded = { isRefreshing = false },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
