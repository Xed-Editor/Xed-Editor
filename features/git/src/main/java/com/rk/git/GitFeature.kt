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
import com.rk.file.FileStatus
import com.rk.file.FileStatusRegistry
import com.rk.file.FileStatusProvider
import com.rk.file.FileChangeNotifier
import java.io.File
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

        // Register FileStatusProvider
        FileStatusRegistry.provider = object : FileStatusProvider {
            override fun getStatus(path: String): FileStatus? {
                val changeType = gitViewModel.get()?.getChangeType(path) ?: return null
                return when (changeType) {
                    ChangeType.ADDED -> FileStatus.ADDED
                    ChangeType.UNTRACKED -> FileStatus.UNTRACKED
                    ChangeType.DELETED -> FileStatus.DELETED
                    ChangeType.CONFLICTING -> FileStatus.CONFLICTING
                    ChangeType.MODIFIED -> FileStatus.MODIFIED
                    ChangeType.RENAMED -> FileStatus.RENAMED
                    else -> FileStatus.MODIFIED
                }
            }
        }

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
        FileChangeNotifier.repositoryOpenListeners.add { root ->
            gitViewModel.get()?.loadRepository(root)
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
