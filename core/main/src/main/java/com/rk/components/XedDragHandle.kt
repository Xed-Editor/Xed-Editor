package com.rk.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rk.theme.DesignTokens

@Composable
fun XedDragHandle(
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val handleColor = if (isDragging) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val handleWidth = if (isDragging) DesignTokens.BottomSheet.dragHandleWidth + 8.dp else DesignTokens.BottomSheet.dragHandleWidth

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .semantics {
                contentDescription = "Drag handle"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = handleWidth, height = DesignTokens.BottomSheet.dragHandleHeight)
                .background(
                    color = handleColor,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}
