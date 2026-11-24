package com.rk.store.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rk.packages.model.PackageAuthor
import com.rk.resources.drawables

@Composable
fun PackageAuthorIcon(author: PackageAuthor, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    AsyncImage(
        model =
            ImageRequest.Builder(context)
                .data(author.github?.let { "https://github.com/$it.png" })
                .fallback(drawables.person)
                .placeholder(drawables.person)
                .error(drawables.person)
                .crossfade(true)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build(),
        contentDescription = null,
        modifier = modifier.clip(CircleShape),
    )
}
