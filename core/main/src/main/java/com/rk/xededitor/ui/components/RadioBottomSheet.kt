package com.rk.xededitor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rk.components.compose.preferences.base.PreferenceTemplate

data class RadioOption<T>(
    val id: T,
    val label: String,
    val description: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> RadioBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    options: List<RadioOption<T>>,
    selectedOption: RadioOption<T>?,
    onOptionSelected: (RadioOption<T>) -> Unit,
    title: String = "Select an option"
) {
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            BottomSheetContent(
                title = { Text(text = title) },
                buttons = {}
            ){
                LazyColumn {
                    items(options) { item ->
                        PreferenceTemplate(
                            title = { Text(text = item.label) },
                            description = {Text(text = item.label)},
                            modifier = Modifier.clickable { onOptionSelected(item)},
                            startWidget = { RadioButton(selected = item == selectedOption, onClick = null) }
                        )

                    }
                }
            }
        }
    }
}
