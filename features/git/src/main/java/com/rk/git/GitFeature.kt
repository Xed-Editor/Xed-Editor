package com.rk.git

import android.app.Application
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
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
import com.rk.utils.isDarkTheme
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.styling.ExtraStylesProvider
import io.github.rosemoe.sora.lang.styling.line.LineAnchorStyle
import io.github.rosemoe.sora.lang.styling.line.LineGutterBackground
import io.github.rosemoe.sora.lang.styling.line.LineSideIcon
import java.lang.ref.WeakReference

// Global reference for gitViewModel
var gitViewModel = WeakReference<GitViewModel?>(null)

class GitFeature : Feature {
    override val toggle =
        FeatureToggle(
            nameRes = strings.git,
            key = "enable_git",
            default = true,
            iconRes = drawables.git,
        )

    private var settingsCategory: SettingsCategory? = null
    private var settingsRoute: DynamicRoute? = null
    private var serviceTabProvider: ServiceTabProvider? = null
    private var addProjectOption: AddProjectOption? = null
    private var dialogProvider: DialogProvider? = null
    private var projectCategory: ProjectCategory? = null
    private val subscriptions = mutableListOf<EventSubscription>()
    private val gitDiffGutterProvider = mutableMapOf<Editor, GitDiffGutterProvider>()

    override fun init(application: Application) {
        // Register Git settings category
        settingsCategory =
            SettingsCategory(
                    labelRes = strings.git,
                    descriptionRes = strings.git_desc,
                    iconRes = drawables.git,
                    route = SettingsRoutes.Git.route,
                )
                .also { SettingsRegistry.registerCategory(it) }

        // Register Git settings route
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

        // Register file change notification listeners
        subscriptions.add(
            Events.subscribe<FileTreeEvent.Opened> { event ->
                val gitRoot = findGitRoot(event.projectRoot.getAbsolutePath())
                if (gitRoot != null) {
                    gitViewModel.get()?.loadRepository(gitRoot)
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
                editor.registerExtraStylesProvider(extraStylesProvider)
            }
        )

        subscriptions.add(
            Events.subscribe<EditorEvent.InstanceDestroyed> { (editor) ->
                gitDiffGutterProvider.remove(editor)
            }
        )

        subscriptions.add(
            Events.subscribe<GitEvent.WorkingTreeUpdated> {
                refreshOpenEditorDiffs()
            }
        )

        // Register Git Clone Overlay and Add Project Sheet action
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
                            // Add file tree tab on success
                            MainActivity.instance?.drawerViewModel?.addFileTreeTab(destination)
                        },
                    )
                }
            }
                .also { DialogRegistry.register(it) }

        // Register Xed project templates
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

    /** Recomputes the gutter line-diffs for every currently open editor with [GitDiffGutterProvider]. */
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
        gitDiffGutterProvider.forEach { (editor, provider) ->
            editor.unregisterExtraStylesProvider(provider)
            provider.dispose()
        }
        gitDiffGutterProvider.clear()
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

/**
 * Provides gutter/line-anchor styling for the editor, and is itself responsible for keeping
 * [GitViewModel.fileLineDiffs] up to date for its editor as the user types. Each instance is tied to an [Editor]
 * instance and must be [dispose]d when that association ends.
 */
class GitDiffGutterProvider(private val editor: Editor) : ExtraStylesProvider {

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

    /** Cancels the content-change subscription. Must be called once this provider is no longer in use. */
    fun dispose() {
        contentChangeSubscription?.unsubscribe()
        contentChangeSubscription = null
    }

    override fun getExtraStyles(line: Int, styles: MutableList<LineAnchorStyle>) {
        if (!Settings.git_gutter_indication) return
        val viewModel = gitViewModel.get() ?: return

        val tab = editor.ownerTab as? EditorTab ?: return
        val file = tab.file ?: return
        val path = file.getAbsolutePath()

        val diffs = viewModel.fileLineDiffs[path] ?: return
        val diffType = diffs[line] ?: return

        val isDark = isDarkTheme(editor.context)

        val colorInt =
            when (diffType) {
                LineDiffType.ADDED -> if (isDark) 0xFF81C784.toInt() else 0xFF2E7D32.toInt()
                LineDiffType.MODIFIED -> if (isDark) 0xFF64B5F6.toInt() else 0xFF1565C0.toInt()
                LineDiffType.DELETED -> 0xFF808080.toInt()
            }

        if (diffType == LineDiffType.DELETED) {
            styles.add(LineSideIcon(line, DotDrawable(colorInt)))
        } else {
            styles.add(LineGutterBackground(line) { colorInt })
        }
    }
}

class DotDrawable(color: Int) : Drawable() {
    private val paint =
        Paint().apply {
            this.color = color
            style = Paint.Style.FILL
            isAntiAlias = true
        }

    override fun draw(canvas: Canvas) {
        val centerX = bounds.centerX().toFloat()
        val centerY = bounds.centerY().toFloat()
        val radius = 6f
        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
