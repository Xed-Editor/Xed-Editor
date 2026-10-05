package com.rk.activities.main.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rk.commands.ActionContext
import com.rk.commands.Command
import com.rk.commands.CommandProvider
import com.rk.drawer.DrawerViewModel
import com.rk.filetree.FileTreeTab
import com.rk.filetree.getAppropriateName
import com.rk.icons.XedIcon
import com.rk.resources.drawables

@Composable
fun WelcomeScreen(drawerViewModel: DrawerViewModel) {
    val drawerTabs by drawerViewModel.drawerTabs.collectAsStateWithLifecycle()
    val currentDrawerTabIndex by drawerViewModel.currentDrawerTabIndex.collectAsStateWithLifecycle()

    val currentDrawerTab = drawerTabs.getOrNull(currentDrawerTabIndex)
    val currentProject = (currentDrawerTab as? FileTreeTab)?.root

    Box(modifier = Modifier.fillMaxSize()) {
        Icon(
            painter = painterResource(drawables.xed_editor),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f),
            modifier = Modifier.align(Alignment.Center).size(420.dp),
        )

        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp).widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            currentProject?.let { project ->
                Text(
                    text = project.getAppropriateName(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.75f),
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = project.getAbsolutePath(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.75f),
                )
            }

            Spacer(Modifier.height(36.dp))

            WelcomeActions(
                commands =
                    listOfNotNull(
                        CommandProvider.BrowseProjectCommand,
                        CommandProvider.NewFileCommand,
                        CommandProvider.SearchFileFolderCommand,
                        CommandProvider.getForId("editor.run"),
                        CommandProvider.getForId("global.terminal"),
                        CommandProvider.DocumentationCommand,
                        CommandProvider.SettingsCommand,
                    )
            )
        }
    }
}

@Composable
private fun WelcomeActions(commands: List<Command>) {
    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        commands.forEach { command ->
            WelcomeActionButton(command = command, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WelcomeActionButton(
    command: Command,
    modifier: Modifier = Modifier,
) {
    if (!command.isSupported() || !command.isEnabled()) return
    val activity = LocalActivity.current

    TextButton(
        modifier = modifier,
        onClick = { activity?.let { command.execute(ActionContext(it)) } },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XedIcon(
                icon = command.getIcon(),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )

            Spacer(Modifier.width(7.dp))

            Text(
                text = command.getLabel(),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
