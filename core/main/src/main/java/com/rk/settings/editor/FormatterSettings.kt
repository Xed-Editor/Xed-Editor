package com.rk.settings.editor

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.InfoBlock
import com.rk.components.compose.preferences.base.LocalIsExpandedScreen
import com.rk.components.compose.preferences.base.NestedScrollStretch
import com.rk.components.compose.preferences.base.PreferenceScaffold
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.editor.FormatterProvider
import com.rk.editor.FormatterSource
import com.rk.editor.Formatters
import com.rk.icons.Icon
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.feature.FeatureRegistry
import com.rk.settings.Settings
import com.rk.utils.handleLazyListScroll
import kotlinx.coroutines.launch

@Composable
fun FormatterSettings(navController: NavController, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val reorderState = rememberReorderState<String>(dragAfterLongPress = true)
    val lazyListState = rememberLazyListState()

    val formatterIds = remember { mutableStateListOf(*Settings.formatters.split("|").toTypedArray()) }
    val formatterSources by remember {
        derivedStateOf {
            formatterIds
                .mapNotNull { id -> Formatters.getSourceForId(id) }
                .toSet()
                .let { it + Formatters.providers.map { provider -> FormatterSource.EXTENSION(provider) } }
                .let { it + FormatterSource.LSP }
        }
    }

    PreferenceScaffold(
        label = stringResource(strings.manage_formatters),
        backArrowVisible = true,
        isExpandedScreen = LocalIsExpandedScreen.current,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(SettingsRoutes.Extensions.route) },
                icon = { Icon(painter = painterResource(drawables.extension), contentDescription = null) },
                text = { Text(stringResource(strings.browse_extensions)) },
            )
        },
    ) { paddingValues ->
        ReorderContainer(state = reorderState, modifier = modifier) {
            NestedScrollStretch {
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom =
                                paddingValues.calculateBottomPadding() +
                                    88.dp, // Add extra space so that FAB doesn't cover content
                            start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                            end = paddingValues.calculateEndPadding(LayoutDirection.Ltr),
                        ),
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    item {
                        InfoBlock(
                            icon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
                            text = stringResource(strings.info_formatters),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    items(items = formatterSources.toList(), key = { Formatters.getIdOf(it) }) { formatterSource ->
                        when (formatterSource) {
                            is FormatterSource.LSP -> {
                                ReorderableItem(
                                    state = reorderState,
                                    key = Formatters.LSP_FORMATTER_ID,
                                    data = Formatters.LSP_FORMATTER_ID,
                                    onDragEnter = { state ->
                                        val index = formatterIds.indexOf(Formatters.LSP_FORMATTER_ID)
                                        if (index == -1) return@ReorderableItem

                                        val oldIndex = formatterIds.indexOf(state.data)

                                        formatterIds.removeAt(oldIndex)
                                        formatterIds.add(index, state.data)
                                        saveOrder(formatterIds)

                                        scope.launch {
                                            handleLazyListScroll(lazyListState = lazyListState, dropIndex = index)
                                        }
                                    },
                                    modifier = Modifier.animateItem(),
                                ) {
                                    DraggableLspFormatter(
                                        modifier =
                                            Modifier.padding(horizontal = 16.dp).graphicsLayer {
                                                alpha = if (isDragging) 0f else 1f
                                            }
                                    )
                                }
                            }
                            is FormatterSource.EXTENSION -> {
                                val formatter = formatterSource.formatter
                                ReorderableItem(
                                    state = reorderState,
                                    key = formatter.id,
                                    data = formatter.id,
                                    onDragEnter = { state ->
                                        val index = formatterIds.indexOf(formatter.id)
                                        if (index == -1) return@ReorderableItem

                                        val oldIndex = formatterIds.indexOf(state.data)

                                        formatterIds.removeAt(oldIndex)
                                        formatterIds.add(index, state.data)
                                        saveOrder(formatterIds)

                                        scope.launch {
                                            handleLazyListScroll(lazyListState = lazyListState, dropIndex = index)
                                        }
                                    },
                                    modifier = Modifier.animateItem(),
                                ) {
                                    DraggableFormatter(
                                        modifier =
                                            Modifier.padding(horizontal = 16.dp).graphicsLayer {
                                                alpha = if (isDragging) 0f else 1f
                                            },
                                        formatter = formatter,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DraggableFormatter(modifier: Modifier = Modifier, formatter: FormatterProvider) {
    val interactionSource = remember { MutableInteractionSource() }
    var checked by remember { mutableStateOf(Formatters.isProviderEnabled(formatter)) }
    val onCheckedChange: (Boolean) -> Unit = {
        Formatters.setProviderEnabled(formatter, it)
        checked = it
    }

    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp, modifier = modifier) {
        PreferenceTemplate(
            modifier =
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    onClick = { onCheckedChange(!checked) },
                ),
            verticalPadding = 8.dp,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ImageVector.vectorResource(drawables.drag_indicator),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 12.dp).size(20.dp),
                    )

                    XedIcon(
                        icon = formatter.icon ?: Icon.ResourceIcon(drawables.auto_fix),
                        modifier = Modifier.padding(end = 8.dp).size(20.dp),
                        contentDescription = formatter.label,
                    )

                    Column {
                        Row { Text(text = formatter.label, style = MaterialTheme.typography.bodyLarge) }
                        Text(
                            text = formatter.supportedExtensions.joinToString(", ") { ".$it" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            },
            endWidget = {
                Switch(interactionSource = interactionSource, checked = checked, onCheckedChange = onCheckedChange)
            },
        )
    }
}

@Composable
fun DraggableLspFormatter(modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    var checked by remember { mutableStateOf(Formatters.isLspFormatterEnabled()) }
    val onCheckedChange: (Boolean) -> Unit = {
        Formatters.setLspFormatterEnabled(it)
        checked = it
    }

    val enabled = FeatureRegistry.isEnabled("feature_terminal")

    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp, modifier = modifier) {
        PreferenceTemplate(
            modifier =
                Modifier.combinedClickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    onClick = { onCheckedChange(!checked) },
                ),
            verticalPadding = 8.dp,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ImageVector.vectorResource(drawables.drag_indicator),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 12.dp).size(20.dp),
                    )

                    XedIcon(
                        icon = Icon.ResourceIcon(drawables.server),
                        modifier = Modifier.padding(end = 8.dp).size(20.dp),
                        contentDescription = stringResource(strings.language_server),
                    )

                    Column {
                        Row {
                            Text(
                                text = stringResource(strings.language_server),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Text(
                            text = stringResource(strings.formatter_lsp_desc),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            },
            endWidget = {
                Switch(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            },
        )
    }
}

/** Save order of formatters in settings */
private fun saveOrder(formatterIds: SnapshotStateList<String>) {
    val formatters = formatterIds.joinToString("|")
    Settings.formatters = formatters
}
