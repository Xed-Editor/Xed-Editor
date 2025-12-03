package com.rk.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                    supportingText = {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    trailingIcon = {
                        if (errorMessage != null) {
                            Icon(XedIcons.Error, "error", tint = MaterialTheme.colorScheme.error)
                        }
                    },
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
