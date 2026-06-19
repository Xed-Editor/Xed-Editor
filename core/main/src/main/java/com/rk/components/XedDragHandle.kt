package com.rk.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun XedDragHandle(
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val handleColor by animateColorAsState(
        targetValue = if (isDragging) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "DragHandleColor",
    )
    val handleWidth by animateDpAsState(
        targetValue = if (isDragging) 48.dp else 36.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "DragHandleWidth",
    )

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
                .size(width = handleWidth, height = 4.dp)
                .background(
                    color = handleColor,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}
