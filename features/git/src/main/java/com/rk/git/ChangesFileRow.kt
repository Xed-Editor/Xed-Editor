package com.rk.git

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.components.compose.utils.addIf
import com.rk.components.getDrawerWidth
import com.rk.filetree.FileNameIcon
import com.rk.utils.drawErrorUnderline

/** A single changed-file row. Includes file icon, name and path. If `checked` is not null, a checkbox is rendered. */
@Composable
fun ChangesFileRow(
    change: GitChange,
    underlineColor: Color?,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val fileName = change.path.substringAfterLast("/")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.width((getDrawerWidth() - 61.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        checked?.let {
            Checkbox(
                enabled = enabled,
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(20.dp),
            )
        }

        FileNameIcon(fileName = fileName, isDirectory = false)

        Text(
            text = fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = change.getColor(),
            modifier = Modifier.addIf(underlineColor != null) { drawErrorUnderline(underlineColor!!) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = change.path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
