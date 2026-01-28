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
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings

fun validateValue(value: String): String? {
    return when {
        value.isBlank() -> {
            strings.value_empty_err.getString()
        }

        else -> null
    }
}

@Composable
fun GitSettings() {
    PreferenceLayout(label = stringResource(id = strings.git)) {
        var showCredentialsDialog by remember { mutableStateOf(false) }
        var showUserDataDialog by remember { mutableStateOf(false) }

        var username by remember { mutableStateOf(Settings.git_username) }
        var password by remember { mutableStateOf(Settings.git_password) }
        var name by remember { mutableStateOf(Settings.git_name) }
        var email by remember { mutableStateOf(Settings.git_email) }

        var usernameError by remember { mutableStateOf<String?>(null) }
        var passwordError by remember { mutableStateOf<String?>(null) }
        var nameError by remember { mutableStateOf<String?>(null) }
        var emailError by remember { mutableStateOf<String?>(null) }

        PreferenceGroup {
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

        if (showCredentialsDialog) {
            DoubleInputDialog(
                title = stringResource(strings.credentials),
                firstInputLabel = stringResource(strings.username),
                firstInputValue = username,
                onFirstInputValueChange = {
                    username = it
                    usernameError = validateValue(username)
                },
                secondInputLabel = stringResource(strings.password),
                secondInputValue = password,
                onSecondInputValueChange = {
                    password = it
                    passwordError = validateValue(password)
                },
                onConfirm = {
                    Settings.git_username = username.toString()
                    Settings.git_password = password.toString()
                },
                onFinish = {
                    username = Settings.git_username
                    password = Settings.git_password
                    usernameError = null
                    passwordError = null
                    showCredentialsDialog = false
                },
                firstErrorMessage = usernameError,
                secondErrorMessage = passwordError,
                confirmEnabled = usernameError == null && passwordError == null && username.isNotBlank(),
            )
        }

        if (showUserDataDialog) {
            DoubleInputDialog(
                title = stringResource(strings.user_data),
                firstInputLabel = stringResource(strings.name),
                firstInputValue = name,
                onFirstInputValueChange = {
                    name = it
                    nameError = validateValue(name)
                },
                secondInputLabel = stringResource(strings.email),
                secondInputValue = email,
                onSecondInputValueChange = {
                    email = it
                    emailError = validateValue(email)
                },
                onConfirm = {
                    Settings.git_name = name.toString()
                    Settings.git_email = email.toString()
                },
                onFinish = {
                    name = Settings.git_name
                    email = Settings.git_email
                    nameError = null
                    emailError = null
                    showUserDataDialog = false
                },
                firstErrorMessage = nameError,
                secondErrorMessage = emailError,
                confirmEnabled = nameError == null && emailError == null && name.isNotBlank(),
            )
        }
    }
}
