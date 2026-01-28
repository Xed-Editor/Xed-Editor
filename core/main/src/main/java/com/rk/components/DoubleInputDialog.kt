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
fun DoubleInputDialog(
    title: String,
    firstInputLabel: String,
    firstInputValue: String,
    onFirstInputValueChange: (String) -> Unit,
    secondInputLabel: String,
    secondInputValue: String,
    onSecondInputValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = {},
    onFinish: () -> Unit = {},
    singleLineMode: Boolean = true,
    confirmText: String = stringResource(strings.apply),
    confirmEnabled: Boolean = true,
    firstErrorMessage: String? = null,
    secondErrorMessage: String? = null,
) {
    val firstFocusRequester = remember { FocusRequester() }
    val secondFocusRequester = remember { FocusRequester() }

    var firstTextFieldValue by remember {
        mutableStateOf(TextFieldValue(firstInputValue, TextRange(firstInputValue.length)))
    }
    var secondTextFieldValue by remember {
        mutableStateOf(TextFieldValue(secondInputValue, TextRange(secondInputValue.length)))
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
                    value = firstTextFieldValue,
                    singleLine = singleLineMode,
                    onValueChange = {
                        firstTextFieldValue = it
                        onFirstInputValueChange(it.text)
                    },
                    label = { Text(firstInputLabel) },
                    modifier = Modifier.fillMaxWidth().focusRequester(firstFocusRequester),
                    isError = firstErrorMessage != null,
                    supportingText =
                        firstErrorMessage?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth()) }
                        },
                    trailingIcon = {
                        if (firstErrorMessage != null) {
                            Icon(XedIcons.Error, stringResource(strings.error), tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    keyboardActions = KeyboardActions(onNext = { secondFocusRequester.requestFocus() }),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                )

                OutlinedTextField(
                    value = secondTextFieldValue,
                    singleLine = singleLineMode,
                    onValueChange = {
                        secondTextFieldValue = it
                        onSecondInputValueChange(it.text)
                    },
                    label = { Text(secondInputLabel) },
                    modifier = Modifier.fillMaxWidth().focusRequester(secondFocusRequester),
                    isError = secondErrorMessage != null,
                    supportingText =
                        secondErrorMessage?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth()) }
                        },
                    trailingIcon = {
                        if (secondErrorMessage != null) {
                            Icon(XedIcons.Error, stringResource(strings.error), tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                )

                LaunchedEffect(Unit) { firstFocusRequester.requestFocus() }
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmEnabled && firstErrorMessage == null && secondErrorMessage == null,
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
