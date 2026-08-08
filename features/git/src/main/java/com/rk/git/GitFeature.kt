package com.rk.git

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import com.rk.activities.main.MainActivity
import com.rk.activities.settings.SettingsRoutes
import com.rk.commands.CommandProvider
import com.rk.components.DialogProvider
import com.rk.components.DialogRegistry
import com.rk.drawer.AddProjectCategory
import com.rk.drawer.AddProjectOption
import com.rk.drawer.AddProjectRegistry
import com.rk.drawer.ServiceTabProvider
import com.rk.drawer.ServiceTabRegistry
import com.rk.editor.Editor
import com.rk.editor.XedColorScheme
import com.rk.events.EditorEvent
import com.rk.events.EditorTabEvent
import com.rk.events.EventSubscription
import com.rk.events.Events
import com.rk.events.FileTreeEvent
import com.rk.extension.api.DynamicRoute
import com.rk.feature.Feature
import com.rk.feature.FeatureRegistry
import com.rk.feature.FeatureToggle
import com.rk.file.FileDecoration
import com.rk.file.FileDecorationProvider
import com.rk.file.FileDecorationRegistry
import com.rk.file.FileObject
import com.rk.file.FilePropertiesProvider
import com.rk.file.FilePropertiesRegistry
import com.rk.file.FileProperty
import com.rk.git.template.ExtensionTemplate
import com.rk.git.template.IconPackTemplate
import com.rk.git.template.ThemeTemplate
import com.rk.icons.Icon
import com.rk.project.ProjectCategory
import com.rk.project.ProjectTemplateRegistry
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.SettingsCategory
import com.rk.settings.SettingsRegistry
import com.rk.settings.git.GitSettings
import com.rk.tabs.editor.EditorTab
import com.rk.theme.gitAdded
import com.rk.theme.gitConflicted
import com.rk.theme.gitDeleted
import com.rk.theme.gitModified
import com.rk.utils.toast
import com.rk.utils.withAlpha
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.InlayHintClickEvent
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint
import io.github.rosemoe.sora.lang.styling.line.LineAnchorStyle
import io.github.rosemoe.sora.lang.styling.line.LineBackground
import io.github.rosemoe.sora.lang.styling.line.LineGutterBackground
import io.github.rosemoe.sora.text.batchEdit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

var gitViewModel = WeakReference<GitViewModel?>(null)

class GitFeature : Feature {
    override val toggle =
        FeatureToggle(
            name = strings.git.getString(),
            key = "enable_git",
            default = true,
            icon = Icon.ResourceIcon(drawables.git),
        )

    private var settingsCategory: SettingsCategory? = null
    private var settingsRoute: DynamicRoute? = null
    private var serviceTabProvider: ServiceTabProvider? = null
    private var addProjectOption: AddProjectOption? = null
    private var dialogProvider: DialogProvider? = null
    private var projectCategory: ProjectCategory? = null
    private val subscriptions = mutableListOf<EventSubscription>()
    private val gitDiffGutterProvider = mutableMapOf<Editor, GitDiffGutterProvider>()
    private val gitConflictStylesProvider = mutableMapOf<Editor, GitConflictStylesProvider>()

    override fun init(application: Application) {
        settingsCategory =
            SettingsCategory(
                    label = strings.git.getString(),
                    description = strings.git_desc.getString(),
                    icon = Icon.ResourceIcon(drawables.git),
                    route = SettingsRoutes.Git.route,
                )
                .also { SettingsRegistry.registerCategory(it) }

        settingsRoute =
            DynamicRoute(SettingsRoutes.Git.route) { _, _ -> GitSettings() }
                .also {
                    SettingsRegistry.registerRoute(it)
                }

        FileDecorationRegistry.register(GitFileDecorationProvider)
        FilePropertiesRegistry.register(GitProperty)

        CommandProvider.registerCommand(GitInitCommand)

        serviceTabProvider =
            ServiceTabProvider { owner ->
                val viewModel = ViewModelProvider(owner)[GitViewModel::class.java]
                gitViewModel = WeakReference(viewModel)
                GitTab(viewModel)
            }
                .also { ServiceTabRegistry.register(it) }

        subscriptions.add(
            Events.subscribe<FileTreeEvent.Opened> { event ->
                val viewModel = gitViewModel.get() ?: return@subscribe
                val gitRoot = findGitRoot(event.projectRoot.getAbsolutePath())
                if (gitRoot != null) {
                    viewModel.loadRepository(gitRoot)
                } else {
                    viewModel.disposeRepository()
                }
            }
        )

        subscriptions.add(
            Events.subscribe<FileTreeEvent.TreeSynchronized> { event ->
                gitViewModel.get()?.syncChanges(event.parent.getAbsolutePath())
            }
        )

        subscriptions.add(
            Events.subscribe<EditorTabEvent.Saved> { event ->
                gitViewModel.get()?.syncChanges(event.file.getAbsolutePath())
            }
        )

        subscriptions.add(
            Events.subscribe<EditorEvent.InstanceCreated> { (editor) ->
                val extraStylesProvider = GitDiffGutterProvider(editor)
                gitDiffGutterProvider[editor] = extraStylesProvider

                val conflictStylesProvider = GitConflictStylesProvider(editor)
                gitConflictStylesProvider[editor] = conflictStylesProvider
            }
        )

        subscriptions.add(
            Events.subscribe<EditorEvent.InstanceDestroyed> { (editor) ->
                gitDiffGutterProvider.remove(editor)?.dispose()
                gitConflictStylesProvider.remove(editor)?.dispose()
            }
        )

        subscriptions.add(
            Events.subscribe<GitEvent.WorkingTreeUpdated> {
                refreshOpenEditorDiffs()
            }
        )

        var showCloneDialog by mutableStateOf(false)
        if (FeatureRegistry.isEnabled("enable_git")) {
            addProjectOption =
                AddProjectOption(
                        icon = Icon.ResourceIcon(drawables.git),
                        title = strings.clone_repo.getString(),
                        description = strings.clone_repo_desc.getString(),
                        category = AddProjectCategory.CREATE,
                        onClick = { onDismiss ->
                            showCloneDialog = true
                            onDismiss()
                        },
                    )
                    .also { AddProjectRegistry.register(it) }
        }

        dialogProvider =
            DialogProvider {
                if (showCloneDialog) {
                    GitCloneDialog(
                        onDismiss = { showCloneDialog = false },
                        onCloneComplete = { destination ->
                            MainActivity.instance?.drawerViewModel?.addFileTreeTab(destination)
                        },
                    )
                }
            }
                .also { DialogRegistry.register(it) }

        projectCategory =
            ProjectCategory(
                    id = "xed_editor",
                    label = strings.app_name.getString(),
                    icon = Icon.ResourceIcon(drawables.xed_editor),
                )
                .also {
                    ProjectTemplateRegistry.registerCategory(it)
                    val templates = listOf(ExtensionTemplate, ThemeTemplate, IconPackTemplate)
                    templates.forEach { template ->
                        ProjectTemplateRegistry.registerTemplate(it, template)
                    }
                }
    }

    private fun refreshOpenEditorDiffs() {
        gitDiffGutterProvider.values.forEach { provider ->
            provider.requestUpdate()
        }
    }

    override fun dispose(application: Application) {
        settingsCategory?.let { SettingsRegistry.unregisterCategory(it) }
        settingsRoute?.let { SettingsRegistry.unregisterRoute(it) }
        FileDecorationRegistry.unregister(GitFileDecorationProvider)
        FilePropertiesRegistry.unregister(GitProperty)
        CommandProvider.unregisterCommand(GitInitCommand)
        serviceTabProvider?.let { ServiceTabRegistry.unregister(it) }
        subscriptions.forEach { it.unsubscribe() }
        subscriptions.clear()
        addProjectOption?.let { AddProjectRegistry.unregister(it) }
        dialogProvider?.let { DialogRegistry.unregister(it) }
        projectCategory?.let {
            val templates = listOf(ExtensionTemplate, ThemeTemplate, IconPackTemplate)
            templates.forEach { template -> ProjectTemplateRegistry.unregisterTemplate(it, template) }
            ProjectTemplateRegistry.unregisterCategory(it)
        }
        gitDiffGutterProvider.forEach { (_, provider) ->
            provider.dispose()
        }
        gitDiffGutterProvider.clear()
        gitConflictStylesProvider.forEach { (_, provider) ->
            provider.dispose()
        }
        gitConflictStylesProvider.clear()
    }
}

object GitProperty : FilePropertiesProvider {
    @Composable
    override fun provideProperties(file: FileObject): List<FileProperty> {
        val changeType = gitViewModel.get()?.getChangeType(file.getAbsolutePath()) ?: return emptyList()
        val gitStatus = changeType.name.lowercase().replaceFirstChar { it.uppercase() }
        val color =
            when (changeType) {
                ChangeType.ADDED,
                ChangeType.UNTRACKED -> MaterialTheme.colorScheme.gitAdded
                ChangeType.DELETED -> MaterialTheme.colorScheme.gitDeleted
                ChangeType.CONFLICTING -> MaterialTheme.colorScheme.gitConflicted
                ChangeType.MODIFIED -> MaterialTheme.colorScheme.gitModified
                ChangeType.RENAMED -> MaterialTheme.colorScheme.gitModified
            }
        return listOf(
            FileProperty(
                label = stringResource(strings.git_status),
                value = gitStatus,
                valueColor = color,
            )
        )
    }
}

object GitFileDecorationProvider : FileDecorationProvider {
    @Composable
    override fun provideDecoration(file: FileObject): FileDecoration? {
        if (!Settings.git_colorize_names) return null
        val changeType = gitViewModel.get()?.getChangeType(file.getAbsolutePath()) ?: return null
        val color =
            when (changeType) {
                ChangeType.ADDED,
                ChangeType.UNTRACKED -> MaterialTheme.colorScheme.gitAdded
                ChangeType.DELETED -> MaterialTheme.colorScheme.gitDeleted
                ChangeType.CONFLICTING -> MaterialTheme.colorScheme.gitConflicted
                ChangeType.MODIFIED -> MaterialTheme.colorScheme.gitModified
                ChangeType.RENAMED -> MaterialTheme.colorScheme.gitModified
            }
        return FileDecoration(color = color)
    }
}

class GitDiffGutterProvider(private val editor: Editor) {

    private var contentChangeSubscription =
        editor.subscribeAlways(ContentChangeEvent::class.java) { _ -> onContentChanged() }

    private fun onContentChanged() {
        requestUpdate()
    }

    fun requestUpdate() {
        val viewModel = gitViewModel.get() ?: return

        val tab = editor.ownerTab as? EditorTab ?: return
        val file = tab.file ?: return
        val path = file.getAbsolutePath()

        viewModel.requestLineDiffUpdate(path, editor.text.toString())
    }

    fun dispose() {
        contentChangeSubscription?.unsubscribe()
        contentChangeSubscription = null
    }

    fun getExtraStyles(line: Int, styles: MutableList<LineAnchorStyle>) {
        if (!Settings.git_gutter_indication) return
        val viewModel = gitViewModel.get() ?: return

        val tab = editor.ownerTab as? EditorTab ?: return
        val file = tab.file ?: return
        val path = file.getAbsolutePath()

        val diffs = viewModel.fileLineDiffs[path] ?: return
        val diffType = diffs[line] ?: return

        val colorInt =
            when (diffType) {
                LineDiffType.ADDED -> editor.colorScheme.getColor(XedColorScheme.GIT_MARKER_ADDED)
                LineDiffType.MODIFIED -> editor.colorScheme.getColor(XedColorScheme.GIT_MARKER_MODIFIED)
                LineDiffType.DELETED -> editor.colorScheme.getColor(XedColorScheme.GIT_MARKER_DELETED)
            }

        styles.add(LineGutterBackground(line) { colorInt })
    }
}

class GitConflictStylesProvider(private val editor: Editor) {
    private var conflicts = mutableListOf<Conflict>()
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var updateJob: Job? = null

    companion object {
        private const val UPDATE_DEBOUNCE_MS = 50L
        private const val CONFLICT_START_MARKER = "<<<<<<<"
        private const val CONFLICT_SEPARATOR = "======="
        private const val CONFLICT_END_MARKER = ">>>>>>>"
    }

    data class Conflict(
        val startLine: Int,
        val middleLine: Int,
        val endLine: Int,
    )

    private data class ConflictActionData(
        val conflict: Conflict,
        val action: ConflictAction,
    )

    private val inlayClickActions = mutableMapOf<Int, ConflictActionData>()

    private val contentChangeSubscription = editor.subscribeAlways(ContentChangeEvent::class.java) { requestUpdate() }

    private val inlayHintClickSubscription =
        editor.subscribeAlways(InlayHintClickEvent::class.java) { event ->
            val hint = event.inlayHint as? TextInlayHint ?: return@subscribeAlways
            val actionData = inlayClickActions[hint.line] ?: return@subscribeAlways
            handleConflictAction(actionData.conflict, actionData.action)
        }

    init {
        requestUpdate()
    }

    private fun requestUpdate() {
        updateJob?.cancel()
        updateJob = scope.launch {
            delay(UPDATE_DEBOUNCE_MS.milliseconds)

            if (!Settings.git_conflict_detection) {
                if (conflicts.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        conflicts = mutableListOf()
                        inlayClickActions.clear()
                        editor.inlayHints = InlayHintsContainer()
                    }
                }
                return@launch
            }

            val text = editor.text
            val newConflicts = mutableListOf<Conflict>()
            var currentConflictStart = -1
            var currentConflictMiddle = -1

            for (i in 0 until text.lineCount) {
                val line = text.getLineString(i)
                if (line.startsWith(CONFLICT_START_MARKER)) {
                    currentConflictStart = i
                } else if (line.startsWith(CONFLICT_SEPARATOR) && currentConflictStart != -1) {
                    currentConflictMiddle = i
                } else if (
                    line.startsWith(CONFLICT_END_MARKER) && currentConflictStart != -1 && currentConflictMiddle != -1
                ) {
                    newConflicts.add(
                        Conflict(
                            startLine = currentConflictStart,
                            middleLine = currentConflictMiddle,
                            endLine = i,
                        )
                    )
                    currentConflictStart = -1
                    currentConflictMiddle = -1
                }
            }

            withContext(Dispatchers.Main) {
                conflicts = newConflicts
                inlayClickActions.clear()
                val container = InlayHintsContainer()

                if (conflicts.isNotEmpty()) {
                    val lineCount = text.lineCount
                    for (conflict in conflicts) {
                        if (conflict.startLine >= lineCount) continue

                        val baseColumn = text.getColumnCount(conflict.startLine)

                        val acceptCurrent = TextInlayHint(
                            conflict.startLine,
                            baseColumn,
                            strings.accept_current_change.getString(),
                        )
                        container.add(acceptCurrent)
                        inlayClickActions[conflict.startLine * 3 + baseColumn] = ConflictActionData(conflict, ConflictAction.ACCEPT_CURRENT)

                        val acceptIncoming = TextInlayHint(
                            conflict.startLine,
                            baseColumn + 1,
                            strings.accept_incoming_change.getString(),
                        )
                        container.add(acceptIncoming)
                        inlayClickActions[conflict.startLine * 3 + baseColumn + 1] = ConflictActionData(conflict, ConflictAction.ACCEPT_INCOMING)

                        val acceptBoth = TextInlayHint(
                            conflict.startLine,
                            baseColumn + 2,
                            strings.accept_both_changes.getString(),
                        )
                        container.add(acceptBoth)
                        inlayClickActions[conflict.startLine * 3 + baseColumn + 2] = ConflictActionData(conflict, ConflictAction.ACCEPT_BOTH)
                    }
                }

                editor.inlayHints = container
            }
        }
    }

    private fun handleConflictAction(conflict: Conflict, action: ConflictAction) {
        val text = editor.text
        val lineCount = text.lineCount

        if (conflict.endLine >= lineCount) {
            return
        }

        text.batchEdit {
            when (action) {
                ConflictAction.ACCEPT_CURRENT -> {
                    text.delete(conflict.middleLine, 0, conflict.endLine + 1, 0)
                    text.delete(conflict.startLine, 0, conflict.startLine + 1, 0)
                }
                ConflictAction.ACCEPT_INCOMING -> {
                    text.delete(conflict.endLine, 0, conflict.endLine + 1, 0)
                    text.delete(conflict.startLine, 0, conflict.middleLine + 1, 0)
                }
                ConflictAction.ACCEPT_BOTH -> {
                    text.delete(conflict.endLine, 0, conflict.endLine + 1, 0)
                    text.delete(conflict.middleLine, 0, conflict.middleLine + 1, 0)
                    text.delete(conflict.startLine, 0, conflict.startLine + 1, 0)
                }
            }
        }

        scope.launch(Dispatchers.Main) {
            when (action) {
                ConflictAction.ACCEPT_CURRENT -> toast(strings.accept_current_change)
                ConflictAction.ACCEPT_INCOMING -> toast(strings.accept_incoming_change)
                ConflictAction.ACCEPT_BOTH -> toast(strings.accept_both_changes)
            }
        }
    }

    fun getExtraStyles(line: Int, styles: MutableList<LineAnchorStyle>) {
        if (Settings.git_conflict_detection.not()) return
        val conflict = conflicts.find { line in it.startLine..it.endLine } ?: return

        val addedColor = editor.colorScheme.getColor(XedColorScheme.GIT_MARKER_ADDED)
        val modifiedColor = editor.colorScheme.getColor(XedColorScheme.GIT_MARKER_MODIFIED)

        val color =
            when {
                line == conflict.startLine -> addedColor
                line < conflict.middleLine -> addedColor withAlpha 0.4f
                line == conflict.middleLine -> return
                line < conflict.endLine -> modifiedColor withAlpha 0.4f
                line == conflict.endLine -> modifiedColor
                else -> return
            }

        styles.add(LineBackground(line) { color })
    }

    fun dispose() {
        contentChangeSubscription.unsubscribe()
        inlayHintClickSubscription.unsubscribe()
        updateJob?.cancel()
        editor.inlayHints = InlayHintsContainer()
    }
}

enum class ConflictAction {
    ACCEPT_CURRENT,
    ACCEPT_INCOMING,
    ACCEPT_BOTH,
}
