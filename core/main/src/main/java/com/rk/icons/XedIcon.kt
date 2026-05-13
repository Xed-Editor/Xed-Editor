package com.rk.icons

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder

@Composable
fun XedIcon(
    icon: Icon,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
) {
    when (icon) {
        is Icon.DrawableRes -> {
            Icon(
                painter = painterResource(icon.drawableRes),
                contentDescription = contentDescription,
                modifier = modifier,
                tint = tint,
            )
        }

        is Icon.VectorIcon -> {
            Icon(imageVector = icon.vector, contentDescription = contentDescription, modifier = modifier, tint = tint)
        }

        is Icon.SvgIcon -> {
            AsyncImage(
                model = icon.file,
                imageLoader = rememberSvgImageLoader(),
                contentDescription = contentDescription,
                modifier = modifier,
                colorFilter = ColorFilter.tint(tint),
            )
        }

        is Icon.TextIcon -> {
            val textSize =
                when (icon.text.length) {
                    in 1..2 -> 14.sp
                    in 3..5 -> 12.sp
                    else -> 10.sp
                }

            Text(text = icon.text, fontFamily = FontFamily.Monospace, color = tint, fontSize = textSize, maxLines = 1)
        }
    }
}

@Composable
fun rememberSvgImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
}
