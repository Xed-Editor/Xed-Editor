package com.rk.settings.runners

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.components.SettingsToggle
import com.rk.components.SingleInputDialog
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings

@Composable
fun HtmlRunnerSettings(modifier: Modifier = Modifier) {
    var showPortDialog by remember { mutableStateOf(false) }
    var portValue by remember { mutableStateOf(Settings.http_server_port.toString()) }
    var portError by remember { mutableStateOf<String?>(null) }

    PreferenceLayout(label = stringResource(strings.html_preview)) {
        PreferenceGroup {
            SettingsToggle(
                label = stringResource(strings.launch_in_browser),
                description = stringResource(strings.launch_in_browser_desc),
                default = Settings.launch_in_browser,
                sideEffect = { Settings.launch_in_browser = it },
            )

            SettingsToggle(
                label = stringResource(strings.inject_eruda),
                description = stringResource(strings.inject_eruda_desc),
                default = Settings.inject_eruda,
                sideEffect = { Settings.inject_eruda = it },
            )

            SettingsToggle(
                label = stringResource(strings.server_port),
                description = stringResource(strings.server_port_desc),
                default = false,
                showSwitch = false,
                onClick = { showPortDialog = true },
            )
        }

        Spacer(modifier = Modifier.height(60.dp))
    }

    if (showPortDialog) {
        SingleInputDialog(
            title = stringResource(strings.server_port),
            inputLabel = stringResource(strings.server_port),
            inputValue = portValue,
            errorMessage = portError,
            confirmEnabled = portValue.isNotBlank(),
            onInputValueChange = {
                portValue = it
                portError = null
                val portInt = portValue.toIntOrNull()
                if (portValue.isBlank() || portInt == null || portInt !in 0..65535) {
                    portError = strings.invalid_port.getString()
                }
            },
            onConfirm = { Settings.http_server_port = portValue.toInt() },
            onFinish = {
                portValue = Settings.http_server_port.toString()
                portError = null
                showPortDialog = false
            },
        )
    }
}
