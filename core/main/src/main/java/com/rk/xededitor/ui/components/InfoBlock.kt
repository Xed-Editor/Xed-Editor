package com.rk.xededitor.ui.components


import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.xededitor.ui.theme.onWarningSurface
import com.rk.xededitor.ui.theme.warningSurface


@SuppressLint("RestrictedApi")
@Composable
fun InfoBlock(
    modifier: Modifier = Modifier,
    text: String,
    icon: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    warning: Boolean = false
) {
    PreferenceGroup(modifier = modifier) {
        Card(
            modifier = modifier,
            shape = shape,
            colors = if (warning) CardDefaults.cardColors(MaterialTheme.colorScheme.warningSurface) else CardDefaults.cardColors()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        icon()
                    }
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warning) MaterialTheme.colorScheme.onWarningSurface else Color.Unspecified,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

