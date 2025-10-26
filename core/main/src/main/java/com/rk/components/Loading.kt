package com.rk.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.rk.resources.strings

@Composable
fun Loading(modifier: Modifier = Modifier, onDismissRequest: () -> Unit, text: String = stringResource(
    strings.wait),dismissOnBackPress: Boolean = false,dismissOnClickOutside: Boolean = false) {

    XedDialog(onDismissRequest, dialogProperties = DialogProperties(dismissOnBackPress = dismissOnBackPress, dismissOnClickOutside = dismissOnClickOutside)) {
        Row(horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator()
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}