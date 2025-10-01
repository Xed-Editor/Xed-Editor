package com.rk.xededitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rk.components.compose.preferences.base.PreferenceGroup

@Composable
fun XedDialog(
    onDismissRequest: () -> Unit,
    dialogProperties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable () -> Unit
) {
    val config = LocalConfiguration.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = dialogProperties
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .clip(shape = RoundedCornerShape(16.dp))
                .width((config.screenWidthDp / 1.25).dp)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
