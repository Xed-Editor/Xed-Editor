package com.rk.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DrawerToolbar(
    modifier: Modifier = Modifier,
    left: @Composable () -> Unit = {},
    right: @Composable () -> Unit = {},
    showDivider: Boolean = true,
    isLoading: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.fillMaxWidth().let {
                    if (showDivider) {
                        it.padding(vertical = 8.dp)
                    } else {
                        it.padding(top = 8.dp)
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) { left() }
            right()
        }

        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxSize())
                } else {
                    HorizontalDivider()
                }
            }
        }
    }
}
