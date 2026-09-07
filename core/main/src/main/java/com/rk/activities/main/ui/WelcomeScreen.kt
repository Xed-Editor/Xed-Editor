package com.rk.activities.main.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.drawer.DrawerViewModel
import com.rk.filetree.FileTreeTab
import com.rk.filetree.getAppropriateName
import com.rk.resources.drawables
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    drawerViewModel: DrawerViewModel,
    drawerState: DrawerState,
    scope: CoroutineScope,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp).widthIn(max = 320.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(72.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(drawables.xed_editor),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(strings.welcome_to).fillPlaceholders(stringResource(strings.app_name)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            val currentDrawerTab = drawerViewModel.currentDrawerTab
            val currentProject = if (currentDrawerTab is FileTreeTab) currentDrawerTab.root else null

            Text(
                text =
                    currentProject?.let {
                        stringResource(strings.selected_project).fillPlaceholders(it.getAppropriateName())
                    } ?: stringResource(strings.no_project_selected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(24.dp))

            Button(onClick = { scope.launch { drawerState.open() } }) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(strings.open_file),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
