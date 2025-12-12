package com.rk.components

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.rk.Icon

@Composable
fun AddDialogItem(@DrawableRes icon: Int, title: String, description: String? = null, onClick: () -> Unit) {
    AddDialogItem(
        title = title,
        description = description,
        onClick = onClick,
        icon = { Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
    )
}

@Composable
fun AddDialogItem(icon: ImageVector, title: String, description: String? = null, onClick: () -> Unit) {
    AddDialogItem(
        title = title,
        description = description,
        onClick = onClick,
        icon = { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
    )
}

@Composable
fun AddDialogItem(icon: Icon, title: String, description: String? = null, onClick: () -> Unit) {
    when (icon) {
        is Icon.DrawableIcon -> {
            fun drawableToPainter(drawable: Drawable): Painter {
                val bitmap =
                    createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1))
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                return BitmapPainter(bitmap.asImageBitmap())
            }

            val painter = remember(LocalContext.current) { drawableToPainter(icon.drawable) }

            AddDialogItem(
                title = title,
                description = description,
                onClick = onClick,
                icon = { Icon(painter = painter, contentDescription = null, modifier = Modifier.size(24.dp)) },
            )
        }

        is Icon.DrawableRes -> {
            AddDialogItem(icon = icon.drawableRes, title = title, description = description, onClick = onClick)
        }

        is Icon.VectorIcon -> {
            AddDialogItem(icon = icon.vector, title = title, description = description, onClick = onClick)
        }
    }
}

@Composable
fun AddDialogItem(icon: @Composable () -> Unit, title: String, description: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()

        Spacer(Modifier.width(16.dp))

        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
