package com.rk.runner

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.components.AddDialogItem
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.strings

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RunnerSheet() {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = {
            RunnerUI.showRunnerDialog = false
            RunnerUI.runnersToShow = emptyList()
        }
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(strings.choose_runner), style = MaterialTheme.typography.titleLarge)

            Column {
                RunnerUI.runnersToShow.forEach { runner ->
                    val activity = LocalActivity.current

                    AddDialogItem(
                        icon = runner.getIcon(context) ?: Icon.ResourceIcon(drawableRes = drawables.run),
                        title = runner.label,
                    ) {
                        activity?.let { runner.run(it) }
                        RunnerUI.showRunnerDialog = false
                        RunnerUI.runnersToShow = emptyList()
                    }
                }
            }
        }
    }
}
