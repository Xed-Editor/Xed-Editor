package com.rk.settings.runners

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.rk.activities.main.MainActivity
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.InfoBlock
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.file.FileWrapper
import com.rk.icons.Error
import com.rk.icons.XedIcons
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.ShellBasedRunner
import com.rk.runner.ShellBasedRunners
import com.rk.settings.Settings
import com.rk.utils.toast
import kotlinx.coroutines.launch

@Composable
fun RunnerSettings(modifier: Modifier = Modifier, navController: NavController) {
    var showDialog by remember { mutableStateOf(false) }
    var runnerName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var regexError by remember { mutableStateOf<String?>(null) }
    var isEditingExisting by remember { mutableStateOf<ShellBasedRunner?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val nameFocusRequester = remember { FocusRequester() }
    val regexFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var regexFieldValue by remember { mutableStateOf(TextFieldValue("", selection = TextRange(runnerName.length))) }

    // Validation functions
    fun validateName() {
        nameError =
            when {
                runnerName.isBlank() -> {
                    strings.name_empty_err.getString()
                }
                !runnerName.matches("^[a-zA-Z0-9_-]+$".toRegex()) -> {
                    strings.invalid_runner_name.getString()
                }
                else -> null
            }
    }

    fun String.isValidRegex(): Boolean =
        try {
            Regex(this)
            true
        } catch (_: Exception) {
            false
        }

    fun validateRegex() {
        regexError =
            if (regexFieldValue.text.isValidRegex()) {
                null
            } else {
                strings.invalid_regex.getString()
            }
    }

    fun resetDialogState() {
        runnerName = ""
        regexFieldValue = regexFieldValue.copy(text = "")
        nameError = null
        regexError = null
        isEditingExisting = null
    }

    LaunchedEffect(Unit) {
        isLoading = true
        ShellBasedRunners.indexRunners()
        isLoading = false
    }

    PreferenceLayout(
        label = stringResource(strings.runners),
        fab = {
            FloatingActionButton(
                onClick = {
                    resetDialogState()
                    showDialog = true
                }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
            }
        },
    ) {
        InfoBlock(
            icon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
            text = stringResource(strings.info_runners),
        )

        PreferenceGroup(heading = stringResource(strings.built_in)) {
            SettingsToggle(
                label = stringResource(strings.html_preview),
                description = stringResource(strings.html_preview_desc),
                default = Settings.enable_html_runner,
                sideEffect = { Settings.enable_html_runner = it },
                onClick = { navController.navigate(SettingsRoutes.HtmlRunner.route) },
            )

            SettingsToggle(
                label = stringResource(strings.markdown_preview),
                description = stringResource(strings.markdown_preview_desc),
                default = Settings.enable_md_runner,
                sideEffect = { Settings.enable_md_runner = it },
            )

            SettingsToggle(
                label = stringResource(strings.universal_runner),
                description = stringResource(strings.universal_runner_desc),
                default = Settings.enable_universal_runner,
                sideEffect = { Settings.enable_universal_runner = it },
            )
        }

        PreferenceGroup(heading = stringResource(strings.external)) {
            val scope = rememberCoroutineScope()
            if (isLoading) {
                SettingsToggle(
                    modifier = Modifier,
                    label = stringResource(strings.loading),
                    default = false,
                    sideEffect = {},
                    showSwitch = false,
                    startWidget = {},
                )
            } else {
                if (ShellBasedRunners.runners.isEmpty()) {
                    SettingsToggle(
                        modifier = Modifier,
                        label = stringResource(strings.no_runners),
                        default = false,
                        sideEffect = {},
                        showSwitch = false,
                        startWidget = {},
                    )
                } else {
                    ShellBasedRunners.runners.forEach { runner ->
                        SettingsToggle(
                            modifier = Modifier,
                            label = runner.getName(),
                            description = null,
                            default = false,
                            sideEffect = { _ ->
                                MainActivity.instance?.let {
                                    it.lifecycleScope.launch {
                                        it.viewModel.newTab(FileWrapper(runner.getScript()))
                                        toast(strings.tab_opened)
                                    }
                                }
                            },
                            showSwitch = false,
                            endWidget = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    IconButton(
                                        onClick = {
                                            isEditingExisting = runner
                                            runnerName = runner.getName()
                                            regexFieldValue =
                                                regexFieldValue.copy(
                                                    text = runner.regex,
                                                    selection = TextRange(runner.regex.length),
                                                )
                                            showDialog = true
                                        }
                                    ) {
                                        Icon(imageVector = Icons.Outlined.Edit, contentDescription = null)
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                ShellBasedRunners.deleteRunner(runner)
                                                // Refresh list
                                                isLoading = true
                                                ShellBasedRunners.indexRunners()
                                                isLoading = false
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(strings.delete),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDialog = false
                    resetDialogState()
                },
                title = {
                    Text(stringResource(if (isEditingExisting != null) strings.edit_runner else strings.new_runner))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = runnerName,
                            onValueChange = {
                                runnerName = it
                                validateName()
                            },
                            label = { Text(stringResource(strings.runner_name)) },
                            modifier = Modifier.focusRequester(nameFocusRequester),
                            isError = nameError != null,
                            supportingText =
                                if (nameError != null) {
                                    {
                                        Text(
                                            text = nameError!!,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        )
                                    }
                                } else null,
                            trailingIcon = {
                                if (nameError != null) {
                                    Icon(
                                        XedIcons.Error,
                                        stringResource(strings.error),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                            enabled = isEditingExisting == null, // Disable name editing for existing runners
                            readOnly = isEditingExisting != null,
                        )

                        OutlinedTextField(
                            value = regexFieldValue,
                            onValueChange = {
                                regexFieldValue = it
                                validateRegex()
                            },
                            label = { Text(stringResource(strings.runner_regex)) },
                            modifier = Modifier.focusRequester(regexFocusRequester),
                            isError = regexError != null,
                            supportingText =
                                if (regexError != null) {
                                    {
                                        Text(
                                            text = regexError!!,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                } else null,
                            trailingIcon = {
                                if (regexError != null) {
                                    Icon(
                                        XedIcons.Error,
                                        stringResource(strings.error),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )

                        LaunchedEffect(showDialog) {
                            if (isEditingExisting == null) {
                                nameFocusRequester.requestFocus()
                            } else {
                                regexFocusRequester.requestFocus()
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = nameError == null && regexError == null && runnerName.isNotBlank(),
                        onClick = {
                            // Check for duplicate names only when creating new runner
                            if (isEditingExisting == null) {
                                if (ShellBasedRunners.runners.any { it.getName() == runnerName }) {
                                    nameError = strings.runner_name_exists.getString()
                                    return@TextButton
                                }
                            }

                            scope.launch {
                                if (isEditingExisting == null) {
                                    // Create new runner
                                    val runner = ShellBasedRunner(name = runnerName, regex = regexFieldValue.text)
                                    val created = ShellBasedRunners.newRunner(runner)
                                    if (created) {
                                        // Refresh list
                                        isLoading = true
                                        ShellBasedRunners.indexRunners()
                                        isLoading = false
                                        showDialog = false
                                        resetDialogState()
                                    } else {
                                        toast(strings.failed)
                                    }
                                } else {
                                    // Update existing runner
                                    val updatedRunner =
                                        ShellBasedRunner(
                                            name = runnerName, // Name remains the same
                                            regex = regexFieldValue.text,
                                        )
                                    ShellBasedRunners.deleteRunner(isEditingExisting!!, deleteScript = false)
                                    val updated = ShellBasedRunners.newRunner(updatedRunner)
                                    if (updated) {
                                        // Refresh list
                                        isLoading = true
                                        ShellBasedRunners.indexRunners()
                                        isLoading = false
                                        showDialog = false
                                        resetDialogState()
                                    } else {
                                        toast(strings.failed)
                                    }
                                }
                            }
                        },
                    ) {
                        Text(stringResource(if (isEditingExisting != null) strings.save else strings.create))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDialog = false
                            resetDialogState()
                        }
                    ) {
                        Text(stringResource(strings.cancel))
                    }
                },
            )
        }
    }
}
