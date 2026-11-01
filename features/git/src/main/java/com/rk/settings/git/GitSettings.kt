package com.rk.settings.git

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.components.DoubleInputDialog
import com.rk.components.PreferenceList
import com.rk.components.PreferenceListItem
import com.rk.components.SettingsItem
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.editor.refreshEditors
import com.rk.utils.openDocs

@Composable
fun GitSettings() {
    PreferenceLayout(label = stringResource(id = strings.git)) {
        var showCredentialsDialog by remember { mutableStateOf(false) }
        var showUserDataDialog by remember { mutableStateOf(false) }
        var showGpgDialog by remember { mutableStateOf(false) }

        var username by remember { mutableStateOf(Settings.git_username) }
        var password by remember { mutableStateOf(Settings.git_password) }
        var name by remember { mutableStateOf(Settings.git_name) }
        var email by remember { mutableStateOf(Settings.git_email) }
        var gpgKey by remember { mutableStateOf(Settings.git_gpg_key) }
        var gpgPassphrase by remember { mutableStateOf(Settings.git_gpg_passphrase) }

        val context = LocalContext.current

        PreferenceGroup(heading = stringResource(strings.general)) {
            SettingsItem(
                label = stringResource(strings.git_colorize_files),
                description = stringResource(strings.git_colorize_files_desc),
                default = Settings.git_colorize_names,
                sideEffect = { Settings.git_colorize_names = it },
            )

            SettingsItem(
                label = stringResource(strings.git_gutter_indication),
                description = stringResource(strings.git_gutter_indication_desc),
                default = Settings.git_gutter_indication,
                sideEffect = { Settings.git_gutter_indication = it },
            )

            SettingsItem(
                label = stringResource(strings.git_conflict_detection),
                description = stringResource(strings.git_conflict_detection_desc),
                default = Settings.git_conflict_detection,
                sideEffect = {
                    Settings.git_conflict_detection = it
                    refreshEditors()
                },
            )
        }

        PreferenceGroup(heading = stringResource(strings.account)) {
            SettingsItem(
                label = stringResource(strings.credentials),
                description = stringResource(strings.credentials_desc),
                showSwitch = false,
                default = false,
                sideEffect = { showCredentialsDialog = true },
            )

            SettingsItem(
                label = stringResource(strings.user_data),
                description = stringResource(strings.user_data_desc),
                showSwitch = false,
                default = false,
                sideEffect = { showUserDataDialog = true },
            )
        }

        PreferenceGroup(heading = stringResource(strings.signing)) {
            SettingsItem(
                label = stringResource(strings.commit_signing),
                description = stringResource(strings.commit_signing_desc),
                default = Settings.git_commit_signing,
                sideEffect = { Settings.git_commit_signing = it },
            )

            PreferenceList(
                label = stringResource(strings.signing_format),
                description = stringResource(strings.signing_format_desc),
                items =
                    listOf(
                        PreferenceListItem("GPG", "GPG"),
                        PreferenceListItem("SSH", "SSH", enabled = false),
                    ),
                selectedItem = Settings.git_signing_format,
                onItemSelected = { Settings.git_signing_format = it },
                enabled = Settings.git_commit_signing,
            )

            SettingsItem(
                label = stringResource(strings.gpg_key),
                description = stringResource(strings.gpg_key_desc),
                showSwitch = false,
                default = false,
                isEnabled = Settings.git_commit_signing,
                sideEffect = { showGpgDialog = true },
            )
        }

        PreferenceGroup(heading = stringResource(strings.repository)) {
            SettingsItem(
                label = stringResource(strings.submodules),
                description = stringResource(strings.submodules_desc),
                default = Settings.git_submodules,
                sideEffect = { Settings.git_submodules = it },
            )

            SettingsItem(
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
                message = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(stringResource(strings.credentials_github), modifier = Modifier.weight(1f))

                        IconButton(onClick = { context.openDocs("git/#connecting-to-github-credentials") }) {
                            Icon(
                                painter = painterResource(drawables.open_in_new),
                                contentDescription = stringResource(strings.docs),
                            )
                        }
                    }
                },
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

        if (showGpgDialog) {
            DoubleInputDialog(
                title = stringResource(strings.gpg_key_input_title),
                firstInputLabel = stringResource(strings.gpg_key_input_label),
                firstInputValue = gpgKey,
                onFirstInputValueChange = { gpgKey = it },
                secondInputLabel = stringResource(strings.gpg_passphrase_input_label),
                secondInputValue = gpgPassphrase,
                onSecondInputValueChange = { gpgPassphrase = it },
                singleLineMode = false,
                onConfirm = {
                    Settings.git_gpg_key = gpgKey
                    Settings.git_gpg_passphrase = gpgPassphrase
                },
                onFinish = {
                    gpgKey = Settings.git_gpg_key
                    gpgPassphrase = Settings.git_gpg_passphrase
                    showGpgDialog = false
                },
            )
        }
    }
}
