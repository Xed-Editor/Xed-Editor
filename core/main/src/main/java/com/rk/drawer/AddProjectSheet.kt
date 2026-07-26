package com.rk.drawer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rk.activities.main.MainActivity
import com.rk.components.AddDialogItem
import com.rk.feature.FeatureRegistry
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.icons.Icon
import com.rk.project.ProjectCreatorActivity
import com.rk.project.ProjectTemplateRegistry
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.Settings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProjectSheet(
    onDismiss: () -> Unit,
    onAddProject: (FileObject) -> Unit,
    openFolder: ManagedActivityResultLauncher<Uri?, Uri?>,
    showPrivateFileWarning: (onOK: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val lifecycleScope = remember { activity.lifecycleScope }

    val viewModel = activity.drawerViewModel

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).verticalScroll(rememberScrollState())
        ) {
            SectionHeader(stringResource(strings.storage))

            AddDialogItem(
                icon = Icon.ResourceIcon(drawables.file_symlink),
                title = stringResource(strings.open_directory),
                description = stringResource(strings.open_dir_desc),
                onClick = {
                    openFolder.launch(null)
                    onDismiss()
                },
            )

            val is11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            val isManager = is11Plus && Environment.isExternalStorageManager()
            val legacyPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED

            val storage = Environment.getExternalStorageDirectory()
            if ((isManager || (!is11Plus && legacyPermission)) && storage.canWrite() && storage.canRead()) {
                AddDialogItem(
                    icon = Icon.ResourceIcon(drawables.android),
                    title = stringResource(strings.internal_storage),
                    description = stringResource(strings.open_internal_storage),
                    onClick = {
                        viewModel.addFileTreeTab(FileWrapper(storage))
                        onDismiss()
                    },
                )
            }

            if (isManager) {
                val storageManager = context.getSystemService(StorageManager::class.java)
                val volumes = storageManager.storageVolumes

                volumes.forEach { volume ->
                    val root = volume.directory ?: return@forEach
                    if (root == storage) return@forEach
                    if (!root.canRead() || !root.canWrite() || root.listFiles() == null) return@forEach

                    val name = volume.getDescription(context)
                    val removable = volume.isRemovable
                    val description = if (removable) strings.open_removable_storage else strings.open_internal_storage

                    AddDialogItem(
                        icon = Icon.ResourceIcon(drawables.sd_card),
                        title = name,
                        description = stringResource(description),
                    ) {
                        viewModel.addFileTreeTab(FileWrapper(root))
                        onDismiss()
                    }
                }
            }

            AddProjectRegistry.options
                .filter { it.category == AddProjectCategory.STORAGE }
                .forEach { option ->
                    AddDialogItem(
                        icon = option.icon,
                        title = option.title,
                        description = option.description,
                        onClick = { option.onClick(onDismiss) },
                    )
                }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(strings.create))

            if (ProjectTemplateRegistry.categories.any { it.templates.isNotEmpty() }) {
                AddDialogItem(
                    icon = Icon.ResourceIcon(drawables.add),
                    title = stringResource(strings.new_project),
                    description = stringResource(strings.new_project_desc),
                    onClick = {
                        context.startActivity(Intent(context, ProjectCreatorActivity::class.java))
                        onDismiss()
                    },
                )
            }

            AddProjectRegistry.options
                .filter { it.category == AddProjectCategory.CREATE }
                .forEach { option ->
                    AddDialogItem(
                        icon = option.icon,
                        title = option.title,
                        description = option.description,
                        onClick = { option.onClick(onDismiss) },
                    )
                }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(strings.other))

            if (FeatureRegistry.isEnabled("debug_mode")) {
                AddDialogItem(
                    icon = Icon.ResourceIcon(drawables.build),
                    title = stringResource(strings.private_files),
                    description = stringResource(strings.private_files_desc),
                    onClick = {
                        if (!Settings.has_shown_private_data_dir_warning) {
                            showPrivateFileWarning {
                                Settings.has_shown_private_data_dir_warning = true
                                lifecycleScope.launch { onAddProject(FileWrapper(activity.filesDir.parentFile!!)) }
                            }
                        } else {
                            lifecycleScope.launch { onAddProject(FileWrapper(activity.filesDir.parentFile!!)) }
                        }
                        onDismiss()
                    },
                )
            }

            AddProjectRegistry.options
                .filter { it.category == AddProjectCategory.OTHER }
                .forEach { option ->
                    AddDialogItem(
                        icon = option.icon,
                        title = option.title,
                        description = option.description,
                        onClick = { option.onClick(onDismiss) },
                    )
                }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() }.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}
