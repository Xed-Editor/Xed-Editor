package com.rk.settings.git

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.rk.components.DoubleInputDialog
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.resources.strings
import com.rk.settings.Settings

@Composable
fun GitSettings() {
    PreferenceLayout(label = stringResource(id = strings.git)) {
        var showCredentialsDialog by remember { mutableStateOf(false) }
        var showUserDataDialog by remember { mutableStateOf(false) }

        var username by remember { mutableStateOf(Settings.git_username) }
        var password by remember { mutableStateOf(Settings.git_password) }
        var name by remember { mutableStateOf(Settings.git_name) }
        var email by remember { mutableStateOf(Settings.git_email) }

        PreferenceGroup(heading = stringResource(strings.account)) {
            SettingsToggle(
                label = stringResource(strings.credentials),
                description = stringResource(strings.credentials_desc),
                showSwitch = false,
                default = false,
                sideEffect = { showCredentialsDialog = true },
            )

            SettingsToggle(
                label = stringResource(strings.user_data),
                description = stringResource(strings.user_data_desc),
                showSwitch = false,
                default = false,
                sideEffect = { showUserDataDialog = true },
            )
        }

        PreferenceGroup(heading = stringResource(strings.repository)) {
            SettingsToggle(
                label = stringResource(strings.submodules),
                description = stringResource(strings.submodules_desc),
                default = Settings.git_submodules,
                sideEffect = { Settings.git_submodules = it },
            )

            SettingsToggle(
                label = stringResource(strings.recursive_submodules),
                description = stringResource(strings.recursive_submodules_desc),
                default = Settings.git_recursive_submodules,
                sideEffect = { Settings.git_recursive_submodules = it },
            )
        }

        if (showCredentialsDialog) {
            DoubleInputDialog(
                title = stringResource(strings.credentials),
                firstInputLabel = stringResource(strings.username),
                firstInputValue = username,
                onFirstInputValueChange = { username = it },
                secondInputLabel = stringResource(strings.password),
                secondInputValue = password,
                onSecondInputValueChange = { password = it },
                onConfirm = {
                    Settings.git_username = username
                    Settings.git_password = password
                },
                onFinish = {
                    username = Settings.git_username
                    password = Settings.git_password
                    showCredentialsDialog = false
                },
            )
        }

        if (showUserDataDialog) {
            DoubleInputDialog(
                title = stringResource(strings.user_data),
                firstInputLabel = stringResource(strings.name),
                firstInputValue = name,
                onFirstInputValueChange = { name = it },
                secondInputLabel = stringResource(strings.email),
                secondInputValue = email,
                onSecondInputValueChange = { email = it },
                onConfirm = {
                    Settings.git_name = name
                    Settings.git_email = email
                },
                onFinish = {
                    name = Settings.git_name
                    email = Settings.git_email
                    showUserDataDialog = false
                },
            )
        }
    }
}
