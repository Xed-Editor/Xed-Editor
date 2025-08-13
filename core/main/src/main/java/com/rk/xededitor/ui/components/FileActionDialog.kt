package com.rk.xededitor.ui.components

import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.compose.filetree.fileTreeViewModel
import com.rk.compose.filetree.removeProject
import com.rk.file.FileObject
import com.rk.libcommons.toast
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.xededitor.ui.activities.main.MainActivity

@Composable
fun FileActionDialog(modifier: Modifier = Modifier,file: FileObject,root: FileObject,onDismissRequest:()-> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    //val lifecycleScope = remember { activity?.lifecycleScope ?: DefaultScope }

    XedDialog(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(0.dp).verticalScroll(rememberScrollState())){

            AddDialogItem(icon = Icons.Outlined.Close, title = stringResource(strings.close), description = stringResource(
                strings.close_current_project), onClick = {
                removeProject(root, true)
                onDismissRequest()
            })

            if (file.isDirectory()){
                AddDialogItem(icon = Icons.Outlined.Refresh, title = stringResource(strings.refresh), description = stringResource(
                    strings.reload_file_tree), onClick = {
                    fileTreeViewModel?.updateCache(file)
                    onDismissRequest()
                })
            }

            AddDialogItem(icon = Icons.Outlined.Edit, title = stringResource(strings.rename), description = stringResource(
                strings.rename_descript), onClick = {
                toast(strings.ni)
                onDismissRequest()
            })

            AddDialogItem(icon = Icons.Outlined.Delete, title = stringResource(strings.delete), description = stringResource(
                strings.delete_descript), onClick = {
                toast(strings.ni)
                onDismissRequest()
            })

            AddDialogItem(icon = if (file.isFile()){drawables.content_copy_24px}else{drawables.round_content_paste_20}, title = stringResource(strings.copy), description = stringResource(
                strings.copy_desc), onClick = {
                toast(strings.ni)
                onDismissRequest()
            })

            AddDialogItem(icon = Icons.Outlined.Delete, title = stringResource(strings.delete), description = stringResource(
                strings.delete_descript), onClick = {
                toast(strings.ni)
                onDismissRequest()
            })

            AddDialogItem(icon = drawables.round_content_paste_20, title = stringResource(strings.paste), description = stringResource(
                strings.paste_desc), onClick = {
                toast(strings.ni)
                onDismissRequest()
            })

            AddDialogItem(icon = Icons.AutoMirrored.Outlined.ExitToApp, title = stringResource(strings.open_with), description = stringResource(
                strings.open_with_other), onClick = {
                toast(strings.ni)
                onDismissRequest()
            })

            AddDialogItem(icon = drawables.file_symlink, title = stringResource(strings.save_as), description = stringResource(
                strings.save_desc), onClick = {
                toast(strings.ni)
                onDismissRequest()
            })

            AddDialogItem(icon = Icons.Outlined.Info, title = stringResource(strings.info), description = stringResource(
                strings.file_info), onClick = {
                toast(strings.ni)
                onDismissRequest()
            })

        }
    }
}