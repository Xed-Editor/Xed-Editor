package com.rk.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private fun TextFieldColors.textColor(enabled: Boolean, isError: Boolean, focused: Boolean): Color =
    when {
        !enabled -> disabledTextColor
        isError -> errorTextColor
        focused -> focusedTextColor
        else -> unfocusedTextColor
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    val finalInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    val textColor =
        textStyle.color.takeOrElse {
            val focused = finalInteractionSource.collectIsFocusedAsState().value
            colors.textColor(enabled, isError, focused)
        }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    BasicTextField(
        cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = mergedTextStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = finalInteractionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
    ) { innerTextField ->
        TextFieldDefaults.DecorationBox(
            value = value,
            visualTransformation = visualTransformation,
            innerTextField = innerTextField,
            singleLine = singleLine,
            enabled = enabled,
            isError = isError,
            interactionSource = finalInteractionSource,
            contentPadding = PaddingValues(top = 8.dp, start = 16.dp, end = 16.dp),
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            label = label,
            shape = shape,
            colors = colors,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    val finalInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    val textColor =
        textStyle.color.takeOrElse {
            val focused = finalInteractionSource.collectIsFocusedAsState().value
            colors.textColor(enabled, isError, focused)
        }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    BasicTextField(
        cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = mergedTextStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = finalInteractionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
    ) { innerTextField ->
        TextFieldDefaults.DecorationBox(
            value = value.text,
            visualTransformation = visualTransformation,
            innerTextField = innerTextField,
            singleLine = singleLine,
            enabled = enabled,
            isError = isError,
            interactionSource = finalInteractionSource,
            contentPadding = PaddingValues(top = 8.dp, start = 16.dp, end = 16.dp),
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            label = label,
            shape = shape,
            colors = colors,
        )
    }
}
