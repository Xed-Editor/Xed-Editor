package com.rk.ai.tab

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.rk.ai.chat.AiChatController
import com.rk.ai.model.AiTodo
import com.rk.ai.model.TodoStatus
import com.rk.theme.greenStatus

/**
 * Toolbar action that opens the agent's task list.
 *
 * The list is session state the agent publishes, so it is presented as a sheet on demand rather than
 * taking up permanent room in the transcript.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTasksButton(controller: AiChatController) {
    var open by remember { mutableStateOf(false) }
    val todos = controller.todos

    IconButton(onClick = { open = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = if (todos.isEmpty()) "Tasks" else "Tasks (${todos.size})",
        )
    }

    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            TasksSheet(
                todos = todos,
                onClear = {
                    controller.clearTodos()
                    open = false
                },
            )
        }
    }
}

@Composable
private fun TasksSheet(todos: List<AiTodo>, onClear: () -> Unit) {
    val done = todos.count { it.status == TodoStatus.Completed }

    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Tasks",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (todos.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(imageVector = Icons.Outlined.Clear,null)
                }

            }
        }

        Spacer(Modifier.height(4.dp))

        if (todos.isEmpty()) return@Column

        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            todos.forEach { todo -> TaskRow(todo) }
        }
    }
}

@Composable
private fun TaskRow(todo: AiTodo) {
    val completed = todo.status == TodoStatus.Completed

    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { TaskStatusIcon(todo.status) },
        headlineContent = {
            Text(
                text = todo.content,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (completed) TextDecoration.LineThrough else null,
                color =
                    if (completed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        },
    )
}

@Composable
private fun TaskStatusIcon(status: TodoStatus) {
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when (status) {
            TodoStatus.Completed ->
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.greenStatus,
                    modifier = Modifier.size(18.dp),
                )
            TodoStatus.InProgress ->
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            TodoStatus.Pending ->
                Box(
                    Modifier.size(16.dp)
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                )
        }
    }
}

