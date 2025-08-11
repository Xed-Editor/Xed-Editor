package com.rk.xededitor.ui.screens.settings.runners

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rk.DefaultScope
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.runnerDir
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.ShellBasedRunner
import com.rk.runner.ShellBasedRunners
import com.rk.xededitor.ui.components.InfoBlock
import com.rk.xededitor.ui.components.SettingsToggle
import kotlinx.coroutines.launch

@Composable
fun Runners(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var runnerName by remember { mutableStateOf("") }
    var regexPattern by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var regexError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ShellBasedRunner?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Validation functions
    fun validateName(): Boolean {
        return if (runnerName.matches("^[a-zA-Z0-9_-]+$".toRegex())) {
            nameError = null
            true
        } else {
            nameError = context.getString(strings.invalid_runner_name)
            false
        }
    }

    fun String.isValidRegex(): Boolean = try {
        Regex(this)
        true
    } catch (e: Exception) {
        false
    }

    fun validateRegex(): Boolean {
        return if (regexPattern.isValidRegex()) {
            regexError = null
            true
        } else {
            regexError = context.getString(strings.invalid_regex)
            false
        }
    }

    fun resetDialogState() {
        runnerName = ""
        regexPattern = ""
        nameError = null
        regexError = null
        editing = null
    }

    LaunchedEffect(Unit) {
        isLoading = true
        ShellBasedRunners.indexRunners()
        isLoading = false
    }

    PreferenceLayout(
        label = stringResource(strings.runners),
        fab = {
            FloatingActionButton(onClick = {
                resetDialogState()
                showDialog = true
            }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
            }
        }
    ) {

        InfoBlock(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Info, contentDescription = null
                )
            },
            text = stringResource(strings.info_runners),
        )

        PreferenceGroup {
            val scope = rememberCoroutineScope()
            if (isLoading) {
                SettingsToggle(
                    modifier = Modifier,
                    label = stringResource(strings.loading),
                    default = false,
                    sideEffect = {},
                    showSwitch = false,
                    startWidget = {}
                )
            } else {
                if (ShellBasedRunners.runners.isEmpty()) {
                    SettingsToggle(
                        modifier = Modifier,
                        label = stringResource(strings.no_runners),
                        default = false,
                        sideEffect = {},
                        showSwitch = false,
                        startWidget = {}
                    )
                } else {
                    ShellBasedRunners.runners.forEach { runner ->
                        SettingsToggle(
                            modifier = Modifier,
                            label = runner.getName(),
                            description = null,
                            default = false,
                            sideEffect = { _ ->
                                toast(strings.tab_opened)
                            },
                            showSwitch = false,
                            endWidget = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    IconButton(onClick = {
                                        editing = runner
                                        runnerName = runner.getName()
                                        regexPattern = runner.regex
                                        showDialog = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = null
                                        )
                                    }

                                    IconButton(onClick = {
                                        scope.launch {
                                            ShellBasedRunners.deleteRunner(runner)
                                            // Refresh list
                                            isLoading = true
                                            ShellBasedRunners.indexRunners()
                                            isLoading = false
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
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
                    Text(
                        stringResource(
                            if (editing != null) strings.edit_runner
                            else strings.new_runner
                        )
                    )
                },
                text = {
                    Column {
                        OutlinedTextField(
                            value = runnerName,
                            onValueChange = { runnerName = it },
                            label = { Text(stringResource(strings.runner_name)) },
                            isError = nameError != null,
                            supportingText = { nameError?.let { Text(it) } },
                            enabled = editing == null, // Disable name editing for existing runners
                            readOnly = editing != null
                        )
                        OutlinedTextField(
                            value = regexPattern,
                            onValueChange = { regexPattern = it },
                            label = { Text(stringResource(strings.regex_pattern)) },
                            isError = regexError != null,
                            supportingText = { regexError?.let { Text(it) } }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val validRegex = validateRegex()
                            val validName = validateName()

                            if (validName && validRegex) {
                                // Check for duplicate names only when creating new runner
                                if (editing == null) {
                                    if (ShellBasedRunners.runners.any { it.getName() == runnerName }) {
                                        nameError = context.getString(strings.runner_name_exists)
                                        return@Button
                                    }
                                }

                                scope.launch {
                                    if (editing == null) {
                                        // Create new runner
                                        val runner = ShellBasedRunner(
                                            name = runnerName,
                                            regex = regexPattern
                                        )
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
                                        val updatedRunner = ShellBasedRunner(
                                            name = runnerName, // Name remains the same
                                            regex = regexPattern
                                        )
                                        ShellBasedRunners.deleteRunner(editing!!,deleteScript = false)
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
                            }
                        }
                    ) {
                        Text(
                            stringResource(
                                if (editing != null) strings.update
                                else strings.create
                            )
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showDialog = false
                            resetDialogState()
                        }
                    ) {
                        Text(stringResource(strings.cancel))
                    }
                }
            )
        }
    }
}