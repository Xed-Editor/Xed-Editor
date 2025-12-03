package com.rk.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

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
