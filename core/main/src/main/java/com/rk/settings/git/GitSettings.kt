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

        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }

        var usernameError by remember { mutableStateOf<String?>(null) }
        var passwordError by remember { mutableStateOf<String?>(null) }

        PreferenceGroup {
            SettingsToggle(
                label = stringResource(strings.credentials),
                description = stringResource(strings.credentials_desc),
                showSwitch = false,
                default = false,
                sideEffect = { showCredentialsDialog = true }
            )

            SettingsToggle(
                label = stringResource(strings.user_data),
                description = stringResource(strings.user_data_desc),
                showSwitch = false,
                default = false,
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
                    // todo
                },
                onFinish = {
                    username = ""
                    password = ""
                    usernameError = null
                    passwordError = null
                    showCredentialsDialog = false
                },
                firstErrorMessage = usernameError,
                secondErrorMessage = passwordError,
                confirmEnabled = usernameError == null && passwordError == null && username.isNotBlank()
            )
        }
    }
}