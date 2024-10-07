package com.rk.xededitor.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import com.rk.xededitor.R

@Composable
fun InputDialog(
    title: String,
    inputLabel: String,
    inputValue: String,
    onInputValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = onInputValueChange,
                    label = { Text(inputLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(stringResource(id = R.string.apply))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onDismiss() }) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}