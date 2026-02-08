package com.rk.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.rk.icons.Error
import com.rk.icons.XedIcons
import com.rk.resources.strings

@Composable
fun SingleInputDialog(
    title: String,
    inputLabel: String,
    inputValue: String,
    onInputValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = {},
    onFinish: () -> Unit = {},
    singleLineMode: Boolean = true,
    confirmText: String = stringResource(strings.apply),
    confirmEnabled: Boolean = true,
    errorMessage: String? = null,
) {
    val focusRequester = remember { FocusRequester() }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = inputValue, selection = TextRange(inputValue.length)))
    }

    AlertDialog(
        onDismissRequest = {
            onDismiss()
            onFinish()
        },
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = textFieldValue,
                    singleLine = singleLineMode,
                    onValueChange = {
                        textFieldValue = it
                        onInputValueChange(it.text)
                    },
                    label = { Text(inputLabel) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    isError = errorMessage != null,
                    supportingText =
                        if (errorMessage != null) {
                            {
                                Text(
                                    text = errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else null,
                    trailingIcon =
                        if (errorMessage != null) {
                            {
                                Icon(
                                    XedIcons.Error,
                                    stringResource(strings.error),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else null,
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }, onSearch = { onConfirm() }),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                )

                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmEnabled && errorMessage == null,
                onClick = {
                    onConfirm()
                    onFinish()
                },
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onFinish()
                }
            ) {
                Text(stringResource(id = strings.cancel))
            }
        },
    )
}
