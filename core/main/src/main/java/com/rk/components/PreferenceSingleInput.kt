package com.rk.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** A Preference that shows a single input dialog when clicked. */
@Composable
fun PreferenceSingleInput(
    label: String,
    description: String?,
    value: String,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    validate: (String) -> String? = { null },
) {
    var showDialog by remember { mutableStateOf(false) }
    var currentValue by remember(value) { mutableStateOf(value) }
    var error by remember { mutableStateOf<String?>(null) }

    SettingsItem(
        modifier = modifier,
        label = label,
        description = description,
        showSwitch = false,
        default = false,
        sideEffect = { showDialog = true },
        isEnabled = enabled,
    )

    if (showDialog) {
        SingleInputDialog(
            title = label,
            inputLabel = label,
            inputValue = currentValue,
            errorMessage = error,
            onInputValueChange = {
                currentValue = it
                error = validate(it)
            },
            onConfirm = { onConfirm(currentValue) },
            onFinish = {
                currentValue = value
                error = null
                showDialog = false
            },
        )
    }
}
