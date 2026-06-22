package com.rk.git

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rk.resources.drawables
import com.rk.resources.strings

@Composable
fun GitCommitArea(
    amend: Boolean,
    commitMessage: String,
    hasCheckedChanges: Boolean,
    isLoading: Boolean,
    onToggleAmend: (Boolean) -> Unit,
    onChangeCommitMessage: (String) -> Unit,
    onCommit: () -> Unit,
    onCommitAndPush: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .toggleable(
                    value = amend,
                    enabled = !isLoading,
                    onValueChange = onToggleAmend,
                    role = Role.Checkbox,
                )
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = amend,
                enabled = !isLoading,
                onCheckedChange = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(strings.amend),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            value = commitMessage,
            onValueChange = onChangeCommitMessage,
            placeholder = { Text(stringResource(strings.commit_message), color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
            textStyle = MaterialTheme.typography.bodySmall,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary.copy(alpha = 0.5f),
                unfocusedBorderColor = colorScheme.outlineVariant,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                enabled = !isLoading && commitMessage.isNotBlank() && hasCheckedChanges,
                modifier = Modifier.weight(1f),
                onClick = onCommit,
                shape = MaterialTheme.shapes.medium,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    painterResource(drawables.commit),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    stringResource(if (amend) strings.amend_commit else strings.commit),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            OutlinedButton(
                enabled = !isLoading && commitMessage.isNotBlank() && hasCheckedChanges,
                modifier = Modifier.weight(1f),
                onClick = onCommitAndPush,
                shape = MaterialTheme.shapes.medium,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    painterResource(drawables.push),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    stringResource(if (amend) strings.amend_commit_and_push else strings.commit_and_push),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
