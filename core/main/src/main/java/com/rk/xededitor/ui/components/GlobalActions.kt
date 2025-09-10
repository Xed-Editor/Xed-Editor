package com.rk.xededitor.ui.components

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.rk.DefaultScope
import com.rk.components.compose.preferences.base.DividerColumn
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.sandboxHomeDir
import com.rk.file.toFileObject
import com.rk.libcommons.application
import com.rk.libcommons.errorDialog
import com.rk.libcommons.showTerminalNotice
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.xededitor.ui.activities.main.MainActivity
import com.rk.xededitor.ui.activities.main.MainViewModel
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import com.rk.xededitor.ui.activities.terminal.Terminal
import kotlinx.coroutines.launch

var addDialog by mutableStateOf(false)

@Composable
fun RowScope.GlobalActions(viewModel: MainViewModel) {
    val context = LocalContext.current
    if (viewModel.tabs.isEmpty()){

        IconButton(onClick = {
            addDialog = true
        }) {
            Icon(imageVector = Icons.Outlined.Add,contentDescription = null)
        }

        IconButton(onClick = {
            showTerminalNotice(MainActivity.instance!!){
                val intent = Intent(context, Terminal::class.java)
                context.startActivity(intent)
            }
        }) {
            Icon(painter = painterResource(drawables.terminal),contentDescription = null)
        }

        IconButton(onClick = {
            val intent = Intent(context,SettingsActivity::class.java)
            context.startActivity(intent)
        }) {
            Icon(imageVector = Icons.Outlined.Settings,contentDescription = null)
        }
    }

    if (addDialog){
        XedDialog(onDismissRequest = {
            addDialog = false
        }) {
            DividerColumn {
                AddDialogItem(icon = drawables.file, title = stringResource(strings.tempFile)) {
                    DefaultScope.launch{
                        viewModel.newTab(FileWrapper(sandboxHomeDir().child("temp").createFileIfNot()),checkDuplicate = true,switchToTab = true)
                    }
                    addDialog = false
                }

                AddDialogItem(icon = Icons.Outlined.Add, title = stringResource(strings.new_file)) {
                    addDialog = false
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.setType("application/octet-stream")
                    intent.putExtra(Intent.EXTRA_TITLE, "newfile.txt")

                    val activities = application!!.packageManager.queryIntentActivities(
                        intent,
                        PackageManager.MATCH_ALL
                    )
                    if (activities.isEmpty()){
                        errorDialog(strings.unsupported_feature)
                    }else{
                        MainActivity.instance?.apply {
                            fileManager.createNewFile(mimeType = "*/*", title = "newfile.txt"){
                                if (it != null){
                                    lifecycleScope.launch{
                                        viewModel.newTab(it,checkDuplicate = true,switchToTab = true)
                                    }
                                }
                            }
                        }
                    }

                }

                AddDialogItem(icon = drawables.file_symlink, title = stringResource(strings.openfile)) {
                    addDialog = false
                    MainActivity.instance?.apply {
                        fileManager.requestOpenFile(mimeType = "*/*"){
                            if (it != null){
                                lifecycleScope.launch{
                                    viewModel.newTab(it.toFileObject(isFile = true),checkDuplicate = true,switchToTab = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }


}