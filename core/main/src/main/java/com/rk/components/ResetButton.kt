package com.rk.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.resources.drawables
import com.rk.resources.strings

@Composable
fun ResetButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors().copy(contentColor = MaterialTheme.colorScheme.error),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painter = painterResource(drawables.refresh), contentDescription = null)
            Text(text = stringResource(id = strings.reset_all))
        }
    }
}

@Composable
fun ResetButtonSmall(modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors().copy(contentColor = MaterialTheme.colorScheme.error),
        onClick = onClick,
    ) {
        Icon(painter = painterResource(drawables.refresh), contentDescription = null)
    }
}
