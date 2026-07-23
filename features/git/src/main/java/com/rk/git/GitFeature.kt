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
import com.rk.components.DialogProvider
import com.rk.components.DialogRegistry
import com.rk.drawer.AddProjectCategory
import com.rk.drawer.AddProjectOption
import com.rk.drawer.AddProjectRegistry
import com.rk.drawer.ServiceTabRegistry
import com.rk.events.EditorTabEvent
import com.rk.events.Events
import com.rk.events.FileTreeEvent
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
import com.rk.settings.SettingsRoute
import com.rk.settings.git.GitSettings
import com.rk.theme.gitAdded
import com.rk.theme.gitConflicted
import com.rk.theme.gitDeleted
import com.rk.theme.gitModified
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

    override fun init(application: Application) {
        // Register Git settings category
        SettingsRegistry.registerCategory(
            SettingsCategory(
                labelRes = strings.git,
                descriptionRes = strings.git_desc,
                iconRes = drawables.git,
                route = SettingsRoutes.Git.route,
            )
        )

        // Register Git settings route
        SettingsRegistry.registerRoute(
            SettingsRoute(SettingsRoutes.Git.route) { _, _ ->
                GitSettings()
            }
        )

        FileDecorationRegistry.register(GitFileDecorationProvider)
        FilePropertiesRegistry.register(GitProperty)

        ServiceTabRegistry.register { owner ->
            val viewModel = ViewModelProvider(owner)[GitViewModel::class.java]
            gitViewModel = WeakReference(viewModel)
            GitTab(viewModel)
        }

        // Register file change notification listeners
        Events.subscribe<FileTreeEvent.Opened> { event ->
            val gitRoot = findGitRoot(event.projectRoot.getAbsolutePath())
            if (gitRoot != null) {
                gitViewModel.get()?.loadRepository(gitRoot)
            }
        }

        Events.subscribe<FileTreeEvent.TreeSynchronized> { event ->
            gitViewModel.get()?.syncChanges(event.parent.getAbsolutePath())
        }

        Events.subscribe<EditorTabEvent.Saved> { event ->
            gitViewModel.get()?.syncChanges(event.file.getAbsolutePath())
        }

        // Register Git Clone Overlay and Add Project Sheet action
        var showCloneDialog by mutableStateOf(false)
        if (FeatureRegistry.isEnabled("enable_git")) {
            val option =
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
            AddProjectRegistry.register(option)
        }

        DialogRegistry.register(
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
        )

        // Register Xed project templates
        val category =
            ProjectCategory(
                id = "xed_editor",
                label = strings.app_name.getString(),
                icon = Icon.ResourceIcon(drawables.xed_editor),
            )
        val templates = listOf(ExtensionTemplate, ThemeTemplate, IconPackTemplate)

        ProjectTemplateRegistry.registerCategory(category)
        templates.forEach { template ->
            ProjectTemplateRegistry.registerTemplate(category, template)
        }
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
        if (!FeatureRegistry.isEnabled("enable_git") || !Settings.git_colorize_names) return null
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
