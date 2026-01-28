package com.rk.git

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.components.isPermanentDrawer
import com.rk.filetree.DrawerTab
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.strings

class GitTab(val viewModel: GitViewModel) : DrawerTab() {
    @Composable
    override fun Content(modifier: Modifier) {
        var showBranchesMenu by remember { mutableStateOf(false) }

        Surface(
            modifier = modifier,
            color =
                if (isPermanentDrawer) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        TextButton(onClick = { showBranchesMenu = true }, enabled = !viewModel.isLoading) {
                            Icon(painterResource(drawables.git), contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(viewModel.currentBranch)
                        }

                        DropdownMenu(expanded = showBranchesMenu, onDismissRequest = { showBranchesMenu = false }) {
                            viewModel.getBranchList().forEach { branch ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = branch == viewModel.currentBranch, onClick = null)
                                            Spacer(Modifier.width(12.dp))
                                            Text(branch)
                                        }
                                    },
                                    onClick = {
                                        viewModel.checkoutBranch(branch)
                                        showBranchesMenu = false
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {}, enabled = !viewModel.isLoading) {
                            Icon(painterResource(drawables.pull), contentDescription = stringResource(strings.pull))
                        }

                        IconButton(onClick = {}, enabled = !viewModel.isLoading) {
                            Icon(painterResource(drawables.fetch), contentDescription = stringResource(strings.fetch))
                        }

                        IconButton(onClick = {}, enabled = !viewModel.isLoading) {
                            Icon(painterResource(drawables.push), contentDescription = stringResource(strings.push))
                        }
                    }
                }

                if (viewModel.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    override fun getName(): String {
        return "Git"
    }

    override fun getIcon(): Icon {
        return Icon.DrawableRes(drawables.git)
    }
}
