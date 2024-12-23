package com.rk.xededitor.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rk.resources.strings
import com.rk.xededitor.R

@Composable
fun InputDialog(
    title: String,
    inputLabel: String,
    inputValue: String,
    onInputValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    singleLineMode:Boolean = true
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = inputValue,
                    singleLine = singleLineMode,
                    onValueChange = onInputValueChange,
                    label = { Text(inputLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardActions = KeyboardActions(
                        onDone = { onConfirm() },
                        onSearch = { onConfirm() }
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text(stringResource(id = strings.apply))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onDismiss() }) { Text(stringResource(id = strings.cancel)) }
        },
    )
}
