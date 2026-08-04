package com.rk.tabs.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rk.resources.drawables
import com.rk.theme.Typography
import com.rk.theme.onWarningSurface
import com.rk.theme.warningSurface

@Composable
fun EditorNotice(text: String, actionButton: @Composable (() -> Unit)? = null) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text,
                    fontSize = Typography.bodyMedium.fontSize,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                actionButton?.invoke()
            }

            HorizontalDivider()
        }
    }
}

@Composable
fun EditorNotice(text: @Composable () -> Unit, actionButton: @Composable (() -> Unit)? = null) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                text()
                actionButton?.invoke()
            }

            HorizontalDivider()
        }
    }
}

@Composable
fun EditorWarningNotice(text: String) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.warningSurface)) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onWarningSurface,
                )

                Text(
                    text,
                    fontSize = Typography.bodyMedium.fontSize,
                    color = MaterialTheme.colorScheme.onWarningSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun EditorErrorNotice(text: String) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer)) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(drawables.error),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )

                Text(
                    text,
                    fontSize = Typography.bodyMedium.fontSize,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()
        }
    }
}
