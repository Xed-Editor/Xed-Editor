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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rk.commands.Command
import com.rk.commands.CommandProvider
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.filetree.rememberSvgImageLoader
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.strings

@Composable
fun DraggableCommand(modifier: Modifier = Modifier, command: Command, onRemove: () -> Unit) {
    val parentLabelState = remember(command.id) { CommandProvider.getParentCommand(command)?.getLabel() }

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

                    when (val icon = command.getIcon()) {
                        is Icon.DrawableRes -> {
                            Icon(
                                painter = painterResource(id = icon.drawableRes),
                                contentDescription = command.getLabel(),
                                modifier = Modifier.padding(end = 8.dp).size(20.dp),
                            )
                        }

                        is Icon.VectorIcon -> {
                            Icon(
                                imageVector = icon.vector,
                                contentDescription = command.getLabel(),
                                modifier = Modifier.padding(end = 8.dp).size(20.dp),
                            )
                        }

                        is Icon.SvgIcon -> {
                            AsyncImage(
                                model = icon.file,
                                imageLoader = rememberSvgImageLoader(),
                                contentDescription = command.getLabel(),
                                modifier = Modifier.padding(end = 8.dp).size(20.dp),
                            )
                        }
                    }

                    Column {
                        Row {
                            command.prefix?.let { Text(text = "$it: ", color = MaterialTheme.colorScheme.primary) }
                            Text(text = command.getLabel(), style = MaterialTheme.typography.bodyLarge)
                        }
                        parentLabelState?.let {
                            Text(
                                text = it,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            },
            endWidget = {
                IconButton(onClick = { onRemove() }) {
                    Icon(imageVector = Icons.Outlined.Delete, stringResource(strings.delete))
                }
            },
        )
    }
}
