package com.rk.components

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.activities.settings.SettingsActivity
import com.rk.activities.terminal.Terminal
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.persistentTempDir
import com.rk.file.toFileObject
import com.rk.icons.CreateNewFile
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.app.InbuiltFeatures
import com.rk.utils.application
import com.rk.utils.errorDialog
import com.rk.utils.showTerminalNotice
import com.rk.utils.toast
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

var addDialog by mutableStateOf(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowScope.GlobalActions(viewModel: MainViewModel) {
    val context = LocalContext.current
    if (viewModel.tabs.isEmpty() || viewModel.currentTab?.showGlobalActions == true) {
        IconButton(onClick = { addDialog = true }) { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) }

        if (InbuiltFeatures.terminal.state.value) {
            IconButton(
                onClick = {
                    showTerminalNotice(MainActivity.instance!!) {
                        val intent = Intent(context, Terminal::class.java)
                        context.startActivity(intent)
                    }
                }
            ) {
                Icon(painter = painterResource(drawables.terminal), contentDescription = null)
            }
        }

        IconButton(
            onClick = {
                val intent = Intent(context, SettingsActivity::class.java)
                context.startActivity(intent)
            }
        ) {
            Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
        }
    }

    if (addDialog) {
        ModalBottomSheet(onDismissRequest = { addDialog = false }) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)) {
                AddDialogItem(icon = drawables.file, title = stringResource(strings.temp_file)) {
                    DefaultScope.launch {
                        var tempFile: FileWrapper? = null
                        var index = 0

                        while (tempFile == null && isActive && index < 10) {
                            val candidate = FileWrapper(persistentTempDir().child("Temp$index"))
                            if (!viewModel.isEditorTabOpened(candidate)) {
                                tempFile = candidate
                            } else {
                                index++
                            }
                        }

                        if (tempFile != null) {
                            tempFile.createFileIfNot()

                            viewModel.newTab(fileObject = tempFile, checkDuplicate = true, switchToTab = true)
                        } else {
                            toast("Temp file limit reached")
                        }
                    }
                    addDialog = false
                }

                AddDialogItem(icon = XedIcons.CreateNewFile, title = stringResource(strings.new_file)) {
                    addDialog = false
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.setType("application/octet-stream")
                    intent.putExtra(Intent.EXTRA_TITLE, "newfile.txt")

                    val activities =
                        application!!.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                    if (activities.isEmpty()) {
                        errorDialog(strings.unsupported_feature)
                    } else {
                        MainActivity.instance?.apply {
                            fileManager.createNewFile(mimeType = "*/*", title = "newfile.txt") {
                                if (it != null) {
                                    lifecycleScope.launch {
                                        viewModel.newTab(it, checkDuplicate = true, switchToTab = true)
                                    }
                                }
                            }
                        }
                    }
                }

                AddDialogItem(icon = drawables.file_symlink, title = stringResource(strings.open_file)) {
                    addDialog = false
                    MainActivity.instance?.apply {
                        fileManager.requestOpenFile(mimeType = "*/*") {
                            if (it != null) {
                                lifecycleScope.launch {
                                    viewModel.newTab(
                                        it.toFileObject(expectedIsFile = true),
                                        checkDuplicate = true,
                                        switchToTab = true,
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
