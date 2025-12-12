package com.rk.settings.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.commands.Command
import com.rk.commands.CommandProvider
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.resources.drawables

@Composable
fun DraggableCommand(modifier: Modifier = Modifier, command: Command, onRemove: () -> Unit) {
    val parentLabelState = remember(command.id) { CommandProvider.getParentCommand(command)?.label }

    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp, modifier = modifier) {
        PreferenceTemplate(
            modifier = Modifier.clickable {},
            verticalPadding = 8.dp,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ImageVector.vectorResource(drawables.drag_indicator),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 12.dp).size(20.dp),
                    )

                    val icon = command.icon.value
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = command.label.value,
                        modifier = Modifier.padding(end = 8.dp).size(20.dp),
                    )

                    Column {
                        Row {
                            command.prefix?.let { Text(text = "$it: ", color = MaterialTheme.colorScheme.primary) }
                            Text(text = command.label.value, style = MaterialTheme.typography.bodyLarge)
                        }
                        parentLabelState?.let {
                            Text(
                                text = it.value,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            },
            endWidget = { IconButton(onClick = { onRemove() }) { Icon(imageVector = Icons.Outlined.Delete, null) } },
        )
    }
}
