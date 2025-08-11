package com.rk.xededitor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.rk.DefaultScope
import com.rk.compose.filetree.removeProject
import com.rk.file.FileObject
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.xededitor.ui.activities.main.MainActivity

@Composable
fun FileActionDialog(modifier: Modifier = Modifier,file: FileObject,root: FileObject,onDismissRequest:()-> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val lifecycleScope = remember { activity?.lifecycleScope ?: DefaultScope }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(strings.add)) },
        text = {
            Column(modifier = Modifier){
                AddDialogItem(icon = Icons.Outlined.Close, title = stringResource(strings.close), description = stringResource(
                    strings.close_current_project), onClick = {
                    removeProject(root, true)
                    onDismissRequest()
                })

                AddDialogItem(icon = Icons.Outlined.Refresh, title = stringResource(strings.refresh), description = stringResource(
                    strings.reload_file_tree), onClick = {
                    toast(strings.ni)
                    onDismissRequest()
                })


            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(strings.cancel))
            }
        }
    )
}