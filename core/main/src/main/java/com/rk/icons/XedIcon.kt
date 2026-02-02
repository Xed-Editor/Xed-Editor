package com.rk.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder

@Composable
fun XedIcon(icon: Icon, modifier: Modifier = Modifier, contentDescription: String? = null) {
    when (icon) {
        is Icon.DrawableRes -> {
            Icon(
                painter = painterResource(icon.drawableRes),
                contentDescription = contentDescription,
                modifier = modifier,
            )
        }

        is Icon.VectorIcon -> {
            Icon(imageVector = icon.vector, contentDescription = contentDescription, modifier = modifier)
        }

        is Icon.SvgIcon -> {
            AsyncImage(
                model = icon.file,
                imageLoader = rememberSvgImageLoader(),
                contentDescription = contentDescription,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun rememberSvgImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
}
