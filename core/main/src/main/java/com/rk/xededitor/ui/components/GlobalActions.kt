package com.rk.xededitor.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.rk.libcommons.toast
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import com.rk.xededitor.ui.activities.main.MainViewModel
import com.rk.xededitor.ui.activities.terminal.Terminal

@Composable
fun RowScope.GlobalActions(viewModel: MainViewModel) {
    val context = LocalContext.current
    if (viewModel.tabs.isEmpty()){

        IconButton(onClick = {
            toast(strings.ni)
        }) {
            Icon(imageVector = Icons.Outlined.Add,contentDescription = null)
        }

        IconButton(onClick = {
            val intent = Intent(context, Terminal::class.java)
            context.startActivity(intent)
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

}