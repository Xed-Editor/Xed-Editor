package com.rk.xededitor.ui.screens.settings.manage

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import coil.compose.AsyncImage

import com.rk.libPlugin.server.Plugin
import com.rk.libPlugin.server.PluginInstaller
import com.rk.libPlugin.server.PluginUtils
import com.rk.libPlugin.server.PluginUtils.getPluginRoot
import com.rk.libPlugin.server.PluginUtils.indexPlugins
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.plugin.*
import com.rk.xededitor.ui.theme.KarbonTheme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.eclipse.jgit.api.Git

import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePluginsScreen(
    onBackPressed: () -> Unit,
    context: Context,
    viewModel: PluginModel = viewModel()
) {
    var showAddPluginDialog by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var selectedPlugin by remember { mutableStateOf<PluginItem?>(null) }
    val coroutineScope = rememberCoroutineScope() 
    var showLoading by remember { mutableStateOf(false) }

    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val fileName = getFileName(uri = it, context = context)
            if (fileName?.endsWith(".zip") == true) {
                showLoading = true

                coroutineScope.launch(Dispatchers.IO) {
                    val isInstalled = context.contentResolver.openInputStream(it)
                        ?.let { inputStream -> PluginInstaller.installFromZip(context, inputStream) } ?: false

                    withContext(Dispatchers.Main) {
                        if (isInstalled) {
                            rkUtils.toast(rkUtils.getString(R.string.install_done))
                            viewModel.loadInstalledPlugins((context as Activity).applicationContext as Application)
                        } else {
                            rkUtils.toast(rkUtils.getString(R.string.install_failed))
                        }
                        showLoading = false
                    }
                }
            } else {
                rkUtils.toast(rkUtils.getString(R.string.invalid_file_type))
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.manage_plugins)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddPluginDialog = true },
                modifier = Modifier.padding(8.dp),
            ) {
                Icon(Icons.Filled.Add, "Floating action button.")
            }
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { innerPadding ->
        var selectedTabIndex by remember { mutableIntStateOf(0) }
        val tabs = listOf(stringResource(R.string.installed), stringResource(R.string.available))

        Column {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            Crossfade(targetState = selectedTabIndex, label = "screens") { screen ->
                when (screen) {
                    0 -> Installed(viewModel = viewModel, context = context)
                    1 -> Available(
                        viewModel = viewModel,
                        onPluginSelected = { plugin ->
                            selectedPlugin = plugin
                            showConfirmationDialog = true
                        }
                    )
                }
            }
        }

        if (showAddPluginDialog) {
            AddPluginDialog(
                onDismissRequest = { showAddPluginDialog = false },
                onConfirm = {
                    pickFileLauncher.launch("*/*")
                    showAddPluginDialog = false
                }
            )
        }

        if (showConfirmationDialog && selectedPlugin != null) {
            ConfirmationDialog(
                onDismissRequest = { 
                    showConfirmationDialog = false 
                    selectedPlugin = null 
                },
                plugin = selectedPlugin!!,
                onConfirm = {
                    coroutineScope.launch(Dispatchers.IO) {
                        (context as Activity).application.indexPlugins()
                        val plugin = selectedPlugin!!
                        if (PluginUtils.getInstalledPlugins().map { it.info.packageName }.contains(plugin.packageName)) {
                            withContext(Dispatchers.Main) {
                                rkUtils.toast(context.getString(R.string.already_installed))
                            }
                        } else {
                            showLoading = true

                            try {
                                Git.cloneRepository()
                                    .setURI(plugin.repo)
                                    .setDirectory(File(context.getPluginRoot(), plugin.title))
                                    .setBranch("main")
                                    .call()

                                withContext(Dispatchers.Main) {
                                    showLoading = false
                                    rkUtils.toast(context.getString(R.string.download_done))
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    showLoading = false
                                    rkUtils.toast("${context.getString(R.string.plugin_download_failed)} : ${e.message}")
                                }
                            }
                        }
                    }
                    showConfirmationDialog = false
                    selectedPlugin = null
                }
            )
        }
        if (showLoading) {
            LoadingIndicatorDialog(
               isShow = showLoading,
               onDismiss = {
                   showLoading = false
               }
            )
        }
    }
}

@Composable
fun Installed(
    viewModel: PluginModel,
    context: Context
) {
    val plugins = viewModel.plugins

    LaunchedEffect(Unit) {
        viewModel.loadInstalledPlugins(context.applicationContext as Application)
    }

    LazyColumn {
        items(plugins) { plugin ->
            val title = plugin.info.name
            val packageName = plugin.info.packageName
            val icon = plugin.info.icon

            var active by remember {
                mutableStateOf(
                    PluginUtils.isPluginActive(
                        context, plugin.info.packageName, false
                    )
                )
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                onClick = {
                    active = !active
                },
            ) {
                Row(modifier = Modifier.padding(8.dp)) {
                    AsyncImage(
                        model = plugin.pluginHome + "/$icon",
                        contentDescription = "Plugin Icon",
                        modifier = Modifier
                            .size(45.dp)
                            .padding(4.dp),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title, style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = packageName, style = MaterialTheme.typography.bodySmall
                        )

                    }

                    Switch(checked = active, onCheckedChange = { checked ->
                        active = checked
                        viewModelScope.launch(Dispatchers.IO) {
                            PluginUtils.setPluginActive(
                                context, plugin.info.packageName, checked
                            )
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun Available(viewModel: PluginModel, onPluginSelected: (PluginItem) -> Unit) {
    if (viewModel.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn {
            items(viewModel.availablePlugins) { plugin ->
                val title = plugin.title

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    onClick = {
                        onPluginSelected(plugin)
                    },
                ) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        AsyncImage(
                            model = plugin.icon,
                            contentDescription = stringResource(R.string.plugin_icon),
                            modifier = Modifier
                                .size(45.dp)
                                .padding(4.dp),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(
                            modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = title, style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = plugin.description, style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddPluginDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "${rkUtils.getString(R.string.add)} ${rkUtils.getString(R.string.plugin)}") },
        text = { Text(text = stringResource(R.string.choose_zip)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.choose_file))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ConfirmationDialog(
    onDismissRequest: () -> Unit,
    plugin: PluginItem,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.download)) },
        text = { Text(text = "${stringResource(R.string.download_sure)} ${plugin.title}?") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.yes))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun LoadingIndicatorDialog(
     isShow: Boolean, 
     onDismiss: () -> Unit
) {
     if (isShow) {
         Dialog(
            onDismissRequest = { onDismiss() },
            DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
         ) {
            Box(
               contentAlignment = Alignment.Center,
               modifier = Modifier
                   .clip(RoundedCornerShape(22.dp))
                   .background(MaterialTheme.colorScheme.surfaceVariant)
               ) {
                   CircularProgressIndicator(
                        modifier = Modifier
                            .padding(16.dp)
                   )
            }
         }
     }
}
private fun getFileName(
    uri: Uri,
    context: Context
): String? {
    var result: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            result = if (nameIndex != -1) {
                it.getString(nameIndex)
            } else {
                null
            }
        }
    }
    return result
}