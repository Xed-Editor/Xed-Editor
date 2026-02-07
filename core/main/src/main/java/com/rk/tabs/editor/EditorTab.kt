package com.rk.tabs.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.DefaultScope
import com.rk.activities.main.EditorCursorState
import com.rk.activities.main.EditorTabState
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.TabState
import com.rk.activities.main.gitViewModel
import com.rk.components.AddDialogItem
import com.rk.components.FindingsDialog
import com.rk.components.SearchPanel
import com.rk.components.SingleInputDialog
import com.rk.components.hasBinaryChars
import com.rk.editor.intelligent.IntelligentFeatureRegistry
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.file.child
import com.rk.icons.Icon
import com.rk.lsp.BaseLspConnector
import com.rk.lsp.formatDocumentSuspend
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.currentRunner
import com.rk.settings.ReactiveSettings
import com.rk.settings.Settings
import com.rk.settings.editor.refreshEditorSettings
import com.rk.settings.support.handleSupport
import com.rk.tabs.base.Tab
import com.rk.utils.errorDialog
import com.rk.utils.getTempDir
import io.github.rosemoe.sora.text.ContentIO
import java.lang.ref.WeakReference
import java.nio.charset.Charset
import java.nio.file.Paths
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.ec4j.core.Cache.Caches
import org.ec4j.core.EditorConfigLoader
import org.ec4j.core.Resource
import org.ec4j.core.ResourcePath
import org.ec4j.core.ResourceProperties
import org.ec4j.core.ResourcePropertiesService
import org.ec4j.core.model.PropertyType

@OptIn(DelicateCoroutinesApi::class)
open class EditorTab(override var file: FileObject, val viewModel: MainViewModel) : Tab() {
    val isTemp: Boolean
        get() {
            return file.getAbsolutePath().startsWith(getTempDir().child("temp_editor").absolutePath)
        }

    private var charset = Charset.forName(Settings.encoding)
    var baseLspConnector: BaseLspConnector? = null

    override val icon: ImageVector
        get() = Icons.Outlined.Edit

    override val name: String
        get() = strings.editor.getString()

    val scope = CoroutineScope(Dispatchers.Default)

    override var tabTitle: MutableState<String> =
        mutableStateOf(file.getName()).also {
            scope.launch(Dispatchers.IO) {
                delay(100)
                val parent = file.getParentFile()
                if (
                    viewModel.tabs.any { it.tabTitle.value == tabTitle.value && it != this@EditorTab } && parent != null
                ) {

                    val title = "${parent.getName()}/${tabTitle.value}"
                    withContext(Dispatchers.Main) { tabTitle.value = title }
                }
            }
        }

    val editorState by mutableStateOf(CodeEditorState())

    override fun onTabRemoved() {
        scope.cancel()
        editorState.content = null
        editorState.editor.get()?.setText("")
        editorState.editor.get()?.release()
        GlobalScope.launch(Dispatchers.IO) { baseLspConnector?.disconnect() }
    }

    init {
        scope.launch {
            if (!file.exists() || !file.canRead()) return@launch

            editorState.editable = !Settings.read_only_default && file.canWrite()
            if (editorState.textmateScope == null) {
                editorState.textmateScope = FileType.getTextmateScopeFromName(file.getName())
            }

            loadEditorConfig()

            if (editorState.content == null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                            editorState.content = file.getInputStream().use { ContentIO.createFrom(it, charset) }
                            editorState.contentLoaded.complete(Unit)

                            if (hasBinaryChars(editorState.content.toString())) {
                                editorState.editable = false
                                showNotice("binary_file") { id ->
                                    EditorNotice(
                                        stringResource(strings.binary_file_notice),
                                        actionButton = {
                                            IconButton(onClick = { removeNotice(id) }) {
                                                Icon(
                                                    painter = painterResource(drawables.close),
                                                    contentDescription = stringResource(strings.close),
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        .onFailure { errorDialog(it) }
                }
            }
        }
    }

    fun showNotice(id: String, notice: @Composable (String) -> Unit) {
        if (editorState.notices.contains(id)) return
        editorState.notices[id] = notice
    }

    fun removeNotice(id: String) {
        editorState.notices.remove(id)
    }

    /** Refresh all normal editor settings and EditorConfig settings and apply them to the editor */
    suspend fun reapplyEditorSettings() {
        val editor = editorState.editor.get()
        editor?.apply {
            applySettings()

            loadEditorConfig()
            editorState.editorConfigLoaded?.await()?.let { applySettings() }
        }
    }

    suspend fun loadEditorConfig() {
        if (!Settings.enable_editorconfig) {
            editorState.editorConfigLoaded = null
            return
        }

        val deferred = CompletableDeferred<ResourceProperties>()
        editorState.editorConfigLoaded = deferred

        withContext(Dispatchers.IO) {
            val cache = Caches.permanent()
            val loader = EditorConfigLoader.default_()
            val propService =
                ResourcePropertiesService.builder()
                    .apply {
                        cache(cache)
                        loader(loader)
                        file.getParentFile()?.getAbsolutePath()?.let {
                            rootDirectory(ResourcePath.ResourcePaths.ofPath(Paths.get(it), charset))
                        }
                    }
                    .build()
            val props =
                propService.queryProperties(Resource.Resources.ofPath(Paths.get(file.getAbsolutePath()), charset))
            deferred.complete(props)

            val editorConfigCharset = props.getValue(PropertyType.charset, null, false)
            editorConfigCharset?.let { charset = Charset.forName(it) }
        }
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val newContent = file.getInputStream().use { ContentIO.createFrom(it, charset) }

            withContext(Dispatchers.Main) {
                editorState.updateLock.withLock {
                    editorState.content = newContent
                    editorState.editor.get()?.setText(newContent)
                    editorState.updateUndoRedo()
                    editorState.isDirty = false
                }
            }
        }
    }

    private val saveMutex = Mutex()

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun save() =
        withContext(Dispatchers.IO) {
            saveMutex.withLock {
                if (Settings.format_on_save && baseLspConnector?.isFormattingSupported() == true) {
                    formatDocumentSuspend(this@EditorTab)
                }

                suspend fun write() {
                    withContext(Dispatchers.IO) {
                        runCatching {
                                if (file.canWrite().not()) {
                                    errorDialog(strings.cant_write)
                                    return@withContext
                                }

                                val content = editorState.content.toString()
                                val normalizedContent = editorState.editor.get()!!.lineEnding.applyOn(content)
                                file.writeText(normalizedContent, charset)

                                editorState.isDirty = false
                                baseLspConnector?.notifySave()
                                Settings.saves += 1

                                MainActivity.instance?.handleSupport()
                            }
                            .onFailure { errorDialog(it) }
                    }
                }

                if (isTemp) {
                    MainActivity.instance?.apply {
                        fileManager.createNewFile(mimeType = "*/*", title = file.getName()) {
                            if (it != null) {
                                file = it
                                tabTitle.value = it.getName()
                                scope.launch {
                                    write()
                                    gitViewModel.get()?.syncChanges(file.getAbsolutePath())!!.join()
                                }
                            }
                        }
                    }
                } else {
                    write()
                    gitViewModel.get()?.syncChanges(file.getAbsolutePath())
                }
            }
        }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current

        key(refreshKey) {
            LaunchedEffect(editorState.editable) { editorState.editor.get()?.editable = editorState.editable }

            Column {
                if (editorState.showRunnerDialog) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            editorState.showRunnerDialog = false
                            editorState.runnersToShow = emptyList()
                        }
                    ) {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)) {
                            editorState.runnersToShow.forEach { runner ->
                                AddDialogItem(
                                    icon = runner.getIcon(context) ?: Icon.DrawableRes(drawableRes = drawables.run),
                                    title = runner.getName(),
                                ) {
                                    DefaultScope.launch {
                                        currentRunner = WeakReference(runner)
                                        runner.run(context, file)
                                        editorState.showRunnerDialog = false
                                        editorState.runnersToShow = emptyList()
                                    }
                                }
                            }
                        }
                    }
                }

                if (editorState.showFindingsDialog) {
                    FindingsDialog(
                        title = editorState.findingsTitle,
                        codeItems = editorState.findingsItems,
                        description = editorState.findingsDescription,
                        onFinish = { editorState.showFindingsDialog = false },
                    )
                }

                if (editorState.showRenameDialog) {
                    SingleInputDialog(
                        title = stringResource(strings.rename_symbol),
                        inputLabel = stringResource(strings.new_name),
                        inputValue = editorState.renameValue,
                        errorMessage = editorState.renameError,
                        confirmEnabled = editorState.renameValue.isNotBlank(),
                        onInputValueChange = {
                            editorState.renameValue = it
                            editorState.renameError = null
                            if (editorState.renameValue.isBlank()) {
                                editorState.renameError = strings.name_empty_err.getString()
                            }
                        },
                        onConfirm = { editorState.renameConfirm?.let { it(editorState.renameValue) } },
                        onFinish = {
                            editorState.renameValue = ""
                            editorState.renameError = null
                            editorState.renameConfirm = null
                            editorState.showRenameDialog = false
                        },
                    )
                }

                if (editorState.showJumpToLineDialog) {
                    SingleInputDialog(
                        title = stringResource(strings.jump_to_line),
                        inputLabel = stringResource(strings.line_number),
                        inputValue = editorState.jumpToLineValue,
                        errorMessage = editorState.jumpToLineError,
                        confirmEnabled = editorState.jumpToLineValue.isNotBlank(),
                        onInputValueChange = {
                            val lastLine = editorState.editor.get()?.lineCount ?: 0

                            editorState.jumpToLineValue = it
                            editorState.jumpToLineError = null
                            if (editorState.jumpToLineValue.toIntOrNull() == null) {
                                editorState.jumpToLineError = strings.value_invalid.getString()
                            } else if (it.toInt() > lastLine) {
                                editorState.jumpToLineError = strings.value_large.getString()
                            } else if (it.toInt() < 1) {
                                editorState.jumpToLineError = strings.value_small.getString()
                            }
                        },
                        onConfirm = { editorState.editor.get()!!.jumpToLine(editorState.jumpToLineValue.toInt() - 1) },
                        onFinish = {
                            editorState.jumpToLineValue = ""
                            editorState.jumpToLineError = null
                            editorState.showJumpToLineDialog = false
                        },
                    )
                }

                Column(modifier = Modifier.animateContentSize()) {
                    SearchPanel(editorState = editorState)
                    if (editorState.isSearching) {
                        HorizontalDivider()
                    }

                    AnimatedVisibility(visible = editorState.isWrapping || editorState.isConnectingLsp) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    editorState.notices.forEach { (id, notice) -> notice(id) }
                }

                val fileExtension = file.getName().substringAfterLast(".")
                val intelligentFeatures =
                    IntelligentFeatureRegistry.allFeatures.filter { feature ->
                        feature.supportedExtensions.contains(fileExtension) && feature.isEnabled()
                    }

                CodeEditor(
                    modifier = Modifier.weight(1f),
                    state = editorState,
                    parentTab = this@EditorTab,
                    intelligentFeatures = intelligentFeatures,
                    onTextChange = {
                        if (Settings.auto_save) {
                            scope.launch(Dispatchers.IO) {
                                save()
                                saveMutex.lock()
                                delay(400)
                                saveMutex.unlock()
                            }
                        }

                        if (file.getName() == ".editorconfig" && Settings.enable_editorconfig) {
                            showNotice("editorconfig_changed") { id ->
                                EditorNotice(
                                    stringResource(strings.editorconfig_changed),
                                    actionButton = {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    save()
                                                    refreshEditorSettings()
                                                    removeNotice(id)
                                                }
                                            }
                                        ) {
                                            Text(stringResource(strings.apply))
                                        }
                                    },
                                )
                            }
                        }
                    },
                )

                if (ReactiveSettings.showExtraKeys) {
                    ExtraKeys(editorTab = this@EditorTab)
                }

                LaunchedEffect(
                    editorState.textmateScope,
                    refreshKey,
                    LocalConfiguration.current,
                    LocalContext.current,
                    MaterialTheme.colorScheme,
                ) {
                    applyHighlightingAndConnectLSP()
                }
            }
        }
    }

    override fun getState(): TabState? {
        val editor = editorState.editor.get() ?: return null
        return EditorTabState(
            fileObject = file,
            cursor =
                EditorCursorState(
                    lineLeft = editor.cursor.leftLine,
                    columnLeft = editor.cursor.leftColumn,
                    lineRight = editor.cursor.rightLine,
                    columnRight = editor.cursor.rightColumn,
                ),
            scrollX = editor.scrollX,
            scrollY = editor.scrollY,
            unsavedContent = if (editorState.isDirty) editor.text.toString() else null,
        )
    }

    @Composable
    override fun RowScope.Actions() {
        EditorActions(modifier = Modifier.Companion, viewModel = viewModel)
    }

    override val showGlobalActions: Boolean = false

    override fun hashCode(): Int {
        return file.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is EditorTab) {
            return false
        }

        return other.file == file
    }

    override fun toString(): String {
        return "[EditorTab] ${file.getAbsolutePath()}"
    }
}
