package com.rk.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.resources.getString
import com.rk.resources.strings

/** A Preference that shows a list of options in a dialog when clicked. */
@Composable
fun <T> PreferenceList(
    label: String,
    description: String?,
    items: List<Pair<T, String>>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showIds: Boolean = false,
    customInputLabel: String? = null,
    customInputValue: String = "",
) {
    var showDialog by remember { mutableStateOf(value = false) }

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
        var tempSelectedItem by remember { mutableStateOf(selectedItem) }
        var tempCustomValue by remember { mutableStateOf(customInputValue) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    items.forEach { (item, string) ->
                        PreferenceTemplate(
                            modifier =
                                Modifier.clip(MaterialTheme.shapes.large).clickable {
                                    tempSelectedItem = item
                                    tempCustomValue = ""
                                },
                            title = { Text(text = string) },
                            description = {
                                if (showIds) Text(text = item.toString())
                            },
                            startWidget = {
                                RadioButton(
                                    selected = (tempSelectedItem == item) && tempCustomValue.isBlank(),
                                    onClick = null,
                                )
                            },
                            verticalPadding = 12.dp,
                        )
                    }

                    if (customInputLabel != null) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tempCustomValue,
                            onValueChange = { tempCustomValue = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(customInputLabel) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        if (tempCustomValue.isNotBlank()) {
                            @Suppress("UNCHECKED_CAST") onItemSelected(tempCustomValue.trim() as T)
                        } else {
                            onItemSelected(tempSelectedItem)
                        }
                    }
                ) {
                    Text(text = strings.apply.getString())
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = strings.cancel.getString())
                }
            },
        )
    }
}
