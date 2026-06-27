package com.rk.git

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.feature.Feature
import com.rk.feature.SettingsRegistry
import com.rk.feature.SettingsCategory
import com.rk.feature.SettingsRoute
import com.rk.activities.settings.SettingsRoutes
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.drawer.AddProjectRegistry
import com.rk.drawer.AddProjectOption
import com.rk.drawer.DrawerOverlayRegistry
import com.rk.drawer.ServiceTabRegistry
import com.rk.icons.Icon
import com.rk.file.FileObject
import com.rk.file.FileDecoration
import com.rk.file.FileDecorationProvider
import com.rk.file.FileDecorationRegistry
import com.rk.file.FileProperty
import com.rk.file.FilePropertiesProvider
import com.rk.file.FilePropertiesRegistry
import com.rk.file.FileChangeNotifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import com.rk.theme.vcsAdded
import com.rk.theme.vcsModified
import com.rk.theme.vcsDeleted
import com.rk.theme.vcsConflicted
import java.lang.ref.WeakReference

// Global reference for gitViewModel
var gitViewModel = WeakReference<GitViewModel?>(null)

class GitFeature : Feature {
    override fun init(application: Application) {
        // Register Git settings category
        SettingsRegistry.registerCategory(
            SettingsCategory(
                labelRes = strings.git,
                descriptionRes = strings.git_desc,
                iconRes = drawables.git,
                route = SettingsRoutes.Git.route
            )
        )

        // Register Git settings route
        SettingsRegistry.registerRoute(
            SettingsRoute(SettingsRoutes.Git.route) {
                com.rk.settings.git.GitSettings()
            }
        )

        // Register FileDecorationProvider
        FileDecorationRegistry.provider = object : FileDecorationProvider {
            @Composable
            override fun getDecoration(file: FileObject): FileDecoration? {
                if (!com.rk.settings.app.InbuiltFeatures.git.state.value || !com.rk.settings.Settings.git_colorize_names) return null
                val changeType = gitViewModel.get()?.getChangeType(file.getAbsolutePath()) ?: return null
                val color = when (changeType) {
                    ChangeType.ADDED,
                    ChangeType.UNTRACKED -> MaterialTheme.colorScheme.vcsAdded
                    ChangeType.DELETED -> MaterialTheme.colorScheme.vcsDeleted
                    ChangeType.CONFLICTING -> MaterialTheme.colorScheme.vcsConflicted
                    ChangeType.MODIFIED -> MaterialTheme.colorScheme.vcsModified
                    ChangeType.RENAMED -> MaterialTheme.colorScheme.vcsModified
                }
                return FileDecoration(color = color)
            }
        }

        // Register FilePropertiesProvider
        FilePropertiesRegistry.providers.add(object : FilePropertiesProvider {
            @Composable
            override fun getProperties(file: FileObject): List<FileProperty> {
                val changeType = gitViewModel.get()?.getChangeType(file.getAbsolutePath()) ?: return emptyList()
                val gitStatus = changeType.name.lowercase().replaceFirstChar { it.uppercase() }
                val color = when (changeType) {
                    ChangeType.ADDED,
                    ChangeType.UNTRACKED -> MaterialTheme.colorScheme.vcsAdded
                    ChangeType.DELETED -> MaterialTheme.colorScheme.vcsDeleted
                    ChangeType.CONFLICTING -> MaterialTheme.colorScheme.vcsConflicted
                    ChangeType.MODIFIED -> MaterialTheme.colorScheme.vcsModified
                    ChangeType.RENAMED -> MaterialTheme.colorScheme.vcsModified
                }
                return listOf(
                    FileProperty(
                        label = stringResource(strings.git_status),
                        value = gitStatus,
                        valueColor = color
                    )
                )
            }
        })

        // Register Service Tab Provider
        ServiceTabRegistry.providers.add { owner ->
            val viewModel: GitViewModel = androidx.lifecycle.ViewModelProvider(owner)[GitViewModel::class.java]
            gitViewModel = WeakReference(viewModel)
            GitTab(viewModel)
        }

        // Register file change notification listeners
        FileChangeNotifier.fileChangeListeners.add { path ->
            gitViewModel.get()?.syncChanges(path)
        }
        FileChangeNotifier.projectOpenListeners.add { root ->

            val gitRoot = findGitRoot(root)
            if (gitRoot != null) {
                gitViewModel.get()?.loadRepository(gitRoot)
            }
        }

        // Register Git Clone Overlay and Add Project Sheet action
        var showCloneDialog by mutableStateOf(false)
        if (com.rk.settings.app.InbuiltFeatures.git.state.value) {
            AddProjectRegistry.options.add(
                AddProjectOption(
                    icon = Icon.ResourceIcon(drawables.git),
                    titleRes = strings.clone_repo,
                    descriptionRes = strings.clone_repo_desc,
                    onClick = { onDismiss ->
                        showCloneDialog = true
                        onDismiss()
                    }
                )
            )
        }

        DrawerOverlayRegistry.overlays.add {
            if (showCloneDialog) {
                GitCloneDialog(
                    onDismiss = { showCloneDialog = false },
                    onCloneComplete = { fileObject ->
                        // Add file tree tab on success
                        com.rk.activities.main.MainActivity.instance?.drawerViewModel?.addFileTreeTab(fileObject)
                    }
                )
            }
        }
    }
}
