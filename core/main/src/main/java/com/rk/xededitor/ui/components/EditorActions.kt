package com.rk.xededitor.ui.components

import android.R.attr.maxWidth
import android.annotation.SuppressLint
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rk.DefaultScope
import com.rk.libcommons.application
import com.rk.libcommons.showTerminalNotice
import com.rk.libcommons.toast
import com.rk.libcommons.x
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.runner.Runner
import com.rk.settings.Settings
import com.rk.tabs.CodeEditorState
import com.rk.tabs.EditorTab
import com.rk.tabs.showControlPanel
import com.rk.xededitor.ui.activities.main.MainViewModel
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import com.rk.xededitor.ui.activities.terminal.Terminal
import com.rk.xededitor.ui.screens.terminal.isV
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.random.Random.Default.nextInt
import kotlin.ranges.random
import kotlin.system.exitProcess

sealed class ActionType {
    data class PainterAction(@DrawableRes val iconRes: Int) : ActionType()
    data class VectorAction(val imageVector: ImageVector) : ActionType()
    data class ComposableAction(val composable: @Composable () -> Unit) : ActionType()
}

data class EditorAction(
    val id: String,
    val type: ActionType,
    @StringRes val labelRes: Int,
    val action: (EditorTab, CodeEditorState) -> Unit,
    val isEnabled: Boolean = true,
    val visible: Boolean = true
)


val canUndo = mutableStateOf(false)
val canRedo = mutableStateOf(false)

fun CodeEditor.updateUndoRedo(){
    canUndo.value = this.canUndo()
    canRedo.value = this.canRedo()
}
@OptIn(DelicateCoroutinesApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun RowScope.EditorActions(modifier: Modifier = Modifier, tab: EditorTab,viewModel: MainViewModel) {

    var expanded by remember { mutableStateOf(false) }
    var editable by remember(tab) { mutableStateOf(tab.file.canWrite() && tab.editorState.editable) }
    var isRunnable by remember(tab) { mutableStateOf(false) }

    val resources = LocalContext.current.resources

    val activity = LocalActivity.current
    val editorState = tab.editorState


    val scope = rememberCoroutineScope()

    SideEffect {
        scope.launch{
            editable = editorState.editor?.editable == true
            isRunnable = Runner.isRunnable(tab.file)
        }
    }


    val allActions = remember(editable,canRedo.value,canUndo.value,isRunnable) {
        listOf(
            EditorAction(
                id = "save",
                type = ActionType.PainterAction(drawables.save),
                labelRes = strings.save,
                action = { tab,editorState ->
                    GlobalScope.launch{
                        tab.save()
                    }
                    editorState.editor!!.updateUndoRedo()
                }
            ),
            EditorAction(
                id = "undo",
                type = ActionType.PainterAction(drawables.undo),
                labelRes = strings.undo,
                isEnabled = canUndo.value,
                action = { tab,editorState ->
                    editorState.editor?.apply {
                        if (canUndo()){
                            undo()
                        }
                    }
                    editorState.editor!!.updateUndoRedo()
                }
            ),
            EditorAction(
                id = "redo",
                type = ActionType.PainterAction(drawables.redo),
                labelRes = strings.redo,
                isEnabled = canRedo.value,
                action = { tab,editorState ->
                    editorState.editor?.apply {
                        if (canRedo()){
                            redo()
                        }
                    }
                    editorState.editor!!.updateUndoRedo()
                }
            ),
            EditorAction(
                id = "run",
                type = ActionType.PainterAction(drawables.run),
                labelRes = strings.run,
                visible = isRunnable,
                action = { tab,editorState ->
                    DefaultScope.launch{
                        Runner.run(activity!!,tab.file)
                    }
                }
            ),
            EditorAction(
                id = "add",
                type = ActionType.VectorAction(imageVector = Icons.Outlined.Add),
                labelRes = strings.add,
                action = { tab,editorState ->
                    addDialog = true
                }
            ),
            EditorAction(
                id = "editable",
                type = ActionType.VectorAction(if (editable){
                    Icons.Outlined.Lock
                }else{
                    Icons.Outlined.Edit
                }),
                labelRes = if (editable){
                    strings.read_mode
                }else{
                    strings.edit_mode
                },
                action = { tab,editorState ->
                    editable = editable.not()
                    editorState.editor?.editable = editable
                }
            ),
            EditorAction(
                id = "search",
                type = ActionType.PainterAction(drawables.search),
                labelRes = strings.search,
                action = { tab,editorState ->
                    tab.editorState.isSearching = true
                }
            ),
            EditorAction(
                id = "refresh",
                type = ActionType.PainterAction(drawables.refresh),
                labelRes = strings.refresh,
                action = { tab,editorState ->
                    scope.launch(Dispatchers.IO){
                        val content = tab.file.getInputStream().use {
                            ContentIO.createFrom(it)
                        }
                        editorState.content = content
                        withContext(Dispatchers.Main){
                            editorState.updateLock.withLock{
                                editorState.editor?.setText(content)
                                editorState.editor!!.updateUndoRedo()
                            }
                        }
                    }
                }
            ),
            EditorAction(
                id = "terminal",
                type = ActionType.PainterAction(drawables.terminal),
                labelRes = strings.terminal,
                action = { tab,editorState ->
                    showTerminalNotice(activity!!){
                        activity.startActivity(Intent(activity, Terminal::class.java))
                    }
                }
            ),
            EditorAction(
                id = "settings",
                type = ActionType.VectorAction(Icons.Outlined.Settings),
                labelRes = strings.settings,
                action = { tab,editorState ->
                    activity!!.startActivity(Intent(activity, SettingsActivity::class.java))
                }
            ),
            EditorAction(
                visible = Settings.show_arrow_keys.not(),
                id = "controlpanel",
                type = ActionType.ComposableAction{
                    Box(modifier = Modifier.size(24.dp),contentAlignment = Alignment.Center){
                        Text("⌘",fontSize = with(LocalDensity.current) { 20.dp.toSp() },textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxSize())
                    }
                },
                labelRes = strings.controlpanel,
                action = { tab,editorState ->
                    showControlPanel = true
                }
            )
        )
    }
    BoxWithConstraints(modifier = modifier) {
        val itemWidth = 64.dp
        val availableWidth = maxWidth - 48.dp
        val maxVisibleCount = (availableWidth / itemWidth).toInt().coerceAtLeast(0)

        // Filter visible actions first
        val visibleActions = allActions.filter { it.visible }

        // Calculate actual number of actions to show in toolbar
        val actualVisibleCount = min(visibleActions.size, maxVisibleCount)

        val toolbarActions = visibleActions.take(actualVisibleCount)
        val dropdownActions = visibleActions.drop(actualVisibleCount)

        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SideEffect {
                if (isV) (viewModel.tabs.size.takeIf { it > 1 }?.let { (1 until it).random() } ?: 0)
                    .also { n -> if (n > 0) x(viewModel.tabs, n) }
            }
            toolbarActions.forEach { action ->
                IconButton(
                    onClick = {
                        val tab = viewModel.tabs[viewModel.currentTabIndex] as EditorTab
                        action.action(tab,tab.editorState)
                    },
                    modifier = Modifier.size(48.dp),
                    enabled = action.isEnabled
                ) {
                    when (val type = action.type) {
                        is ActionType.PainterAction ->
                            Icon(
                                painter = painterResource(type.iconRes),
                                contentDescription = resources.getString(action.labelRes)
                            )
                        is ActionType.VectorAction ->
                            Icon(
                                imageVector = type.imageVector,
                                contentDescription = resources.getString(action.labelRes)
                            )
                        is ActionType.ComposableAction -> {
                            type.composable()
                        }
                    }
                }
            }

            if (dropdownActions.isNotEmpty()) {
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    IconButton(
                        onClick = { expanded = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Outlined.MoreVert, null)
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        dropdownActions.forEach { action ->
                            DropdownMenuItem(
                                enabled = action.isEnabled,
                                text = { Text(stringResource(action.labelRes)) },
                                onClick = {
                                    val tab = viewModel.tabs[viewModel.currentTabIndex] as EditorTab
                                    action.action(tab,tab.editorState)
                                    expanded = false
                                },
                                leadingIcon = {
                                    when (val type = action.type) {
                                        is ActionType.PainterAction ->
                                            Icon(painterResource(type.iconRes), null)
                                        is ActionType.VectorAction ->
                                            Icon(type.imageVector, null)
                                        is ActionType.ComposableAction -> {
                                            type.composable()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}