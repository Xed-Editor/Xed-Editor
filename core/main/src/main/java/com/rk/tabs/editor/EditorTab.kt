package com.rk.tabs.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.rk.activities.main.EditorCursorState
import com.rk.activities.main.EditorTabState
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.TabState
import com.rk.activities.main.searchViewModel
import com.rk.color.ColorPicker
import com.rk.components.SingleInputDialog
import com.rk.editor.intelligent.IntelligentFeatureRegistry
import com.rk.events.EditorTabEvent
import com.rk.events.Events
import com.rk.extension.api.XedExtensionPoint
import com.rk.file.FileObject
import com.rk.file.FileTypeManager
import com.rk.lsp.LspConnector
import com.rk.lsp.formatDocumentSuspend
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.search.EditorSearchPanel
import com.rk.search.FindingsDialog
import com.rk.settings.Settings
import com.rk.settings.editor.refreshEditorSettings
import com.rk.settings.support.handleSupport
import com.rk.tabs.base.Tab
import com.rk.utils.errorDialog
import com.rk.utils.hasBinaryChars
import io.github.rosemoe.sora.text.ContentIO
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
import java.nio.charset.Charset
import java.nio.file.Paths
import kotlin.time.Duration.Companion.milliseconds

@OptIn(DelicateCoroutinesApi::class)
open class EditorTab(
    override var file: FileObject?,
    var projectRoot: FileObject?,
    val viewModel: MainViewModel,
    isReadOnly: Boolean = false,
    private val customTitle: String? = null,
    val fallbackExtension: String? = null,
) : Tab() {

    var isReadOnly: Boolean = isReadOnly
        private set

    val isTemp: Boolean
        get() = file == null && !isReadOnly

    private var taskCount = 0

    private var charset = Charset.forName(Settings.encoding)
    var lspConnector: LspConnector? = null

    override val icon: ImageVector
        get() = Icons.Outlined.Edit

    override val name: String
        get() = strings.editor.getString()

    val scope = CoroutineScope(Dispatchers.Default)

    override var tabTitle: MutableState<String> =
        mutableStateOf(customTitle ?: file?.getName() ?: strings.temp_file.getString()).also {
            val file = file
            if (file != null) {
                scope.launch(Dispatchers.IO) {
                    val parent = file.getParentFile()
                    if (
                        viewModel.tabs.any { it.tabTitle.value == tabTitle.value && it != this@EditorTab } &&
                            parent != null
                    ) {
                        val title = "${parent.getName()}/${tabTitle.value}"
                        withContext(Dispatchers.Main) { tabTitle.value = title }
                    }
                }
            }
        }

    val editorState by mutableStateOf(CodeEditorState())

    override fun onTabRemoved() {
        scope.cancel()
        editorState.content = null
        editorState.editor.get()?.setText("")
        editorState.editor.get()?.release()
        GlobalScope.launch(Dispatchers.IO) { lspConnector?.disconnect() }
    }

    override fun onTabSelected() {
        editorState.editor.get()?.apply {
            requestFocus()
            requestFocusFromTouch()
        }
    }

    override fun onTabUnselected() {
        editorState.editor.get()?.clearFocus()
    }

    init {
        scope.launch {
            val file = file
            if (file == null) {
                editorState.contentLoaded.complete(Unit)
                editorState.editable = !isReadOnly
                return@launch
            }

            if (!file.exists() || !file.canRead()) return@launch
            if (!file.canWrite()) {
                this@EditorTab.isReadOnly = true
            }

            projectRoot = projectRoot ?: file.getParentFile()

            editorState.editable = !isReadOnly && !Settings.read_only_default
            if (editorState.textmateScope == null) {
                editorState.textmateScope = FileTypeManager.fromFileName(file.getName()).textmateScope
            }

            loadEditorConfig()

            if (editorState.content == null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        editorState.content = file.getInputStream().use { ContentIO.createFrom(it, charset) }
                        editorState.contentLoaded.complete(Unit)

                        if (Settings.detect_bin_files && hasBinaryChars(editorState.content.toString())) {
                            editorState.editable = false
                            showNotice(BINARY_NOTICE_KEY) { id -> BinaryNotice(id) }
                        }
                    }
                        .onFailure { errorDialog(throwable = it) }
                }
            }
        }
    }

    companion object {
        private const val BINARY_NOTICE_KEY = "binary_file"
        private const val EDITORCONFIG_NOTICE_KEY = "editorconfig_changed"
    }

    @Composable
    private fun BinaryNotice(id: String) {
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

    @Composable
    private fun EditorConfigNotice(id: String) {
        EditorNotice(
            text = stringResource(strings.editorconfig_changed),
            actionButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
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

    @XedExtensionPoint
    fun showNotice(id: String, notice: @Composable (String) -> Unit) {
        if (editorState.notices.contains(id)) return
        editorState.notices[id] = notice
    }

    @XedExtensionPoint
    fun removeNotice(id: String) {
        editorState.notices.remove(id)
    }

    /** Refresh all normal editor settings and EditorConfig settings and apply them to the editor */
    suspend fun reapplyEditorSettings() {
        val editor = editorState.editor.get()
        editor?.apply {
            applySettings()

            if (file != null) {
                loadEditorConfig()
                editorState.editorConfigLoaded?.await()?.let { applySettings(it) }
            }

            val isTxtFile = file?.getName()?.endsWith(".txt") ?: (fallbackExtension == "txt")
            if (Settings.word_wrap_text && isTxtFile) {
                setWordwrap(true, true, true)
            }
        }
    }

    suspend fun loadEditorConfig() {
        val file = file
        if (!Settings.enable_editorconfig || file == null) {
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
        val file = file ?: return
        scope.launch(Dispatchers.IO) {
            if (!file.exists() || !file.canRead()) return@launch

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

    private suspend fun write() {
        val file = file ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                if (!file.canWrite()) {
                    errorDialog(strings.cant_write)
                    return@withContext
                }

                val content = editorState.content.toString()
                val normalizedContent = editorState.editor.get()!!.lineEnding.applyOn(content)
                file.writeText(normalizedContent, charset)

                editorState.isDirty = false
                lspConnector?.notifySave()
            }
                .onFailure { errorDialog(throwable = it) }
        }
    }

    suspend fun quickSave() = saveMutex.withLock {
        if (isReadOnly) return@withLock
        if (isTemp) return@withLock
        val file = file ?: return@withLock

        write()

        searchViewModel.get()?.syncIndex(file)
        Events.publish(EditorTabEvent.Saved(this, file, true))
    }

    fun saveAs() {
        MainActivity.instance?.apply {
            fileManager.createNewFile(mimeType = "*/*", title = file?.getName() ?: "untitled.txt") {
                if (it != null) {
                    file = it
                    tabTitle.value = it.getName()
                    editorState.textmateScope = FileTypeManager.fromFileName(it.getName()).textmateScope
                    scope.launch {
                        write()
                        searchViewModel.get()?.syncIndex(it)
                        Events.publish(EditorTabEvent.Saved(this@EditorTab, it, false))
                    }
                }
            }
        }
    }

    suspend fun save() = saveMutex.withLock {
        if (isReadOnly) return@withLock
        if (Settings.format_on_save && lspConnector?.isFormattingSupported() == true) {
            formatDocumentSuspend(this@EditorTab)
        }

        val file = file ?: return@withLock
        if (isTemp) {
            withContext(Dispatchers.Main) { saveAs() }
            return@withLock
        }

        write()

        searchViewModel.get()?.syncIndex(file)
        Events.publish(EditorTabEvent.Saved(this, file, false))

        Settings.saves += 1
        MainActivity.instance?.handleSupport()
    }

    @XedExtensionPoint
    suspend fun withTask(block: suspend () -> Unit) {
        registerTask()
        try {
            block()
        } finally {
            unregisterTask()
        }
    }

    fun registerTask() {
        taskCount++
    }

    fun unregisterTask() {
        if (taskCount > 0) {
            taskCount--
        }
    }

    fun isTaskInProcess(): Boolean {
        return taskCount > 0
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        key(refreshKey) {
            LaunchedEffect(editorState.editable) { editorState.editor.get()?.editable = editorState.editable }

            Column {
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
                            val editor = editorState.editor.get()
                            val lastLine = editor?.lineCount ?: 0

                            editorState.jumpToLineValue = it
                            editorState.jumpToLineError = null

                            val parts = it.split(":")

                            val line = parts.getOrNull(0)?.toIntOrNull()
                            val column = parts.getOrNull(1)?.toIntOrNull()

                            when {
                                line == null -> {
                                    editorState.jumpToLineError = strings.value_invalid.getString()
                                }

                                column == null && parts.size > 1 -> {
                                    editorState.jumpToLineError = strings.value_invalid.getString()
                                }

                                parts.size > 2 -> {
                                    editorState.jumpToLineError = strings.value_invalid.getString()
                                }

                                line > lastLine -> {
                                    editorState.jumpToLineError = strings.value_large.getString()
                                }

                                line < 1 -> {
                                    editorState.jumpToLineError = strings.value_small.getString()
                                }

                                column != null && column < 1 -> {
                                    editorState.jumpToLineError = strings.value_small.getString()
                                }
                            }
                        },
                        onConfirm = {
                            val editor = editorState.editor.get() ?: return@SingleInputDialog
                            val parts = editorState.jumpToLineValue.split(":")

                            val line = parts[0].toInt() - 1
                            val maxColumn = editor.text.getLine(line).length - 1
                            val column = parts.getOrNull(1)?.toInt()?.minus(1)?.coerceIn(0, maxColumn) ?: 0

                            editor.setSelection(line, column)
                        },
                        onFinish = {
                            editorState.jumpToLineValue = ""
                            editorState.jumpToLineError = null
                            editorState.showJumpToLineDialog = false
                        },
                    )
                }

                Column(modifier = Modifier.animateContentSize()) {
                    EditorSearchPanel(editorState = editorState)
                    if (editorState.isSearching) {
                        HorizontalDivider()
                    }

                    AnimatedVisibility(visible = isTaskInProcess()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    editorState.notices.forEach { (id, notice) -> notice(id) }
                }

                val fileExtension = file?.getExtension() ?: fallbackExtension ?: "txt"
                val intelligentFeatures =
                    IntelligentFeatureRegistry.allFeatures.filter { feature ->
                        feature.supportedExtensions.contains(fileExtension) && feature.isEnabled()
                    }

                CodeEditor(
                    modifier = Modifier.weight(1f),
                    intelligentFeatures = intelligentFeatures,
                    onTextChange = {
                        if (Settings.auto_save && !isTemp && file != null) {
                            scope.launch(Dispatchers.IO) {
                                quickSave()
                                saveMutex.lock()
                                delay(Settings.auto_save_delay.milliseconds)
                                saveMutex.unlock()
                            }
                        } else {
                            editorState.isDirty = true
                        }

                        if (file?.getName() == ".editorconfig" && Settings.enable_editorconfig) {
                            showNotice(EDITORCONFIG_NOTICE_KEY) { id -> EditorConfigNotice(id) }
                        }
                    },
                )

                val showColorPicker = editorState.showColorPicker
                if (showColorPicker != null) {
                    ColorPicker(
                        initialColor = showColorPicker.first,
                        initialFormat = showColorPicker.second,
                        onApply = {
                            val textRange = editorState.colorPickerRange ?: return@ColorPicker
                            val editor = editorState.editor.get() ?: return@ColorPicker
                            editor.text.replace(
                                textRange.start.line,
                                textRange.start.column,
                                textRange.end.line,
                                textRange.end.column,
                                it,
                            )
                        },
                    ) {
                        editorState.showColorPicker = null
                    }
                }

                if (Settings.show_extra_keys) {
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
            projectRoot = projectRoot,
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
            isReadOnly = isReadOnly,
        )
    }

    @Composable
    override fun RowScope.Actions() {
        EditorToolbarActions(viewModel = viewModel)
    }

    override val showGlobalActions: Boolean = false

    override fun hashCode(): Int {
        return file?.hashCode() ?: customTitle?.hashCode() ?: super.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is EditorTab) {
            return false
        }

        return if (file != null && other.file != null) {
            other.file == file
        } else if (file == null && other.file == null) {
            customTitle == other.customTitle
        } else {
            false
        }
    }

    override fun toString(): String {
        return "[EditorTab] ${file?.getAbsolutePath() ?: customTitle}"
    }
}
