package com.rk.xededitor.ui.screens.settings.plugin

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.rk.plugin.server.PluginInfo
import com.rk.plugin.server.PluginInstaller
import com.rk.plugin.server.PluginUtils
import com.rk.plugin.server.PluginUtils.getPluginRoot
import com.rk.plugin.server.PluginUtils.indexPlugins
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePluginsScreen(
  onBackPressed: () -> Unit, activity: Activity, viewModel: PluginViewModel = viewModel()
) {
  var showAddPluginDialog by remember { mutableStateOf(false) }
  var showDownloadDialog by remember { mutableStateOf(false) }
  
  var selectedPlugin by remember { mutableStateOf<PluginInfo?>(null) }
  
  val coroutineScope = rememberCoroutineScope()
  
  val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      val fileName = getFileName(uri = it, activity = activity)
      if (fileName?.endsWith(".zip") == true) {
        LoadingPopup(activity, null).let { popup ->
          popup.show()
          coroutineScope.launch(Dispatchers.IO) {
            val isInstalled = activity.contentResolver.openInputStream(it)?.let { inputStream ->
              PluginInstaller.installFromZip(
                activity, inputStream
              )
            } ?: false
            
            withContext(Dispatchers.Main) {
              if (isInstalled) {
                rkUtils.toast(rkUtils.getString(R.string.install_done))
                viewModel.loadInstalledPlugins((activity as Activity).applicationContext as Application)
              } else {
                rkUtils.toast(rkUtils.getString(R.string.install_failed))
              }
              popup.hide()
            }
          }
          
          popup.hide()
        }
        
        
      } else {
        rkUtils.toast(rkUtils.getString(R.string.invalid_file_type))
      }
    }
  }
  var selectedTabIndex by remember { mutableIntStateOf(0) }
  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      TopAppBar(title = { Text(text = stringResource(R.string.manage_plugins)) }, navigationIcon = {
        IconButton(onClick = onBackPressed) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
          )
        }
      })
    },
    floatingActionButton = {
      if (selectedTabIndex == 0) {
        FloatingActionButton(
          onClick = { showAddPluginDialog = true },
          modifier = Modifier.padding(8.dp),
        ) {
          Icon(Icons.Filled.Add, "FAB")
        }
      }
    },
    floatingActionButtonPosition = FabPosition.End,
  ) { innerPadding ->
    val tabs = listOf(stringResource(R.string.installed), stringResource(R.string.available))
    //val layoutDirection = LocalLayoutDirection.current
    
    Column {
      TabRow(
        selectedTabIndex = selectedTabIndex, modifier = Modifier
          .fillMaxWidth()
          .padding(innerPadding)
          .padding(top = 5.dp)
      ) {
        tabs.forEachIndexed { index, title ->
          Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }, text = { Text(title) })
        }
      }
      
      Crossfade(targetState = selectedTabIndex, label = "screens") { screen ->
        when (screen) {
          0 -> InstalledPlugins(viewModel = viewModel, activity = activity)
          1 -> AvailablePlugins(viewModel = viewModel, onPluginSelected = { plugin ->
            selectedPlugin = plugin
            showDownloadDialog = true
          })
        }
      }
    }
    
    if (showAddPluginDialog) {
      AddPluginDialog(onDismissRequest = { showAddPluginDialog = false }, onConfirm = {
        pickFileLauncher.launch("*/*")
        showAddPluginDialog = false
      })
    }
    
    if (showDownloadDialog && (selectedPlugin != null)) {
      if (PluginUtils.getInstalledPlugins().map { it.info.packageName }.contains(selectedPlugin!!.packageName)) {
        rkUtils.toast(activity.getString(R.string.already_installed))
      }else{
        DownloadDialog(onDismissRequest = {
          showDownloadDialog = false
        }, plugin = selectedPlugin!!, onConfirm = {
          
          coroutineScope.launch(Dispatchers.IO) {
            activity.application.indexPlugins()
            val plugin = selectedPlugin!!
            if (PluginUtils.getInstalledPlugins().map { it.info.packageName }.contains(plugin.packageName)) {
              withContext(Dispatchers.Main) {
                rkUtils.toast(activity.getString(R.string.already_installed))
                
              }
            } else {
              
              LoadingPopup(activity, null).apply {
                show()
                try {
                  Git.cloneRepository().setURI(plugin.repo).setDirectory(File(activity.getPluginRoot(), plugin.title)).setBranch("main").call()
                  
                  withContext(Dispatchers.Main) {
                    
                    rkUtils.toast(activity.getString(R.string.download_done))
                  }
                } catch (e: Exception) {
                  e.printStackTrace()
                  withContext(Dispatchers.Main) {
                    
                    rkUtils.toast("${activity.getString(R.string.plugin_download_failed)} : ${e.message}")
                  }
                } finally {
                  hide()
                }
              }
              
            }
          }
          showDownloadDialog = false
        })
      }
    }
  }
}

@Composable
fun InstalledPlugins(
  viewModel: PluginViewModel, 
  activity: Context
) {
  val plugins = viewModel.plugins
  val coroutineScope = rememberCoroutineScope()
  
  LaunchedEffect(Unit) {
    viewModel.loadInstalledPlugins(activity.applicationContext as Application)
  }
  
  Column {
    if (!viewModel.isLoading && plugins.isNotEmpty()) {
      PreferenceGroup(heading = stringResource(id = R.string.installed)) {
        plugins.forEach { plugin ->
          var active by remember {
            mutableStateOf(
              PluginUtils.isPluginActive(
                activity, plugin.info.packageName, false
              )
            )
          }
          PluginRow(plugin.info, true, active) {
            active = !active
            coroutineScope.launch(Dispatchers.IO) {
              PluginUtils.setPluginActive(
                activity, plugin.info.packageName, active
              )
            }
          }
        }
      }
    } else if (!viewModel.isLoading) {
      NoContentScreen(label = stringResource(id = R.string.nip))
    }
  }
}

@Composable
fun AvailablePlugins(
  viewModel: PluginViewModel, 
  onPluginSelected: (PluginInfo) -> Unit
) {
  if (viewModel.isLoading) {
    Column(
      modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
      CircularProgressIndicator()
    }
  } else if (viewModel.availablePlugins.isNotEmpty()) {
    Column {
      PreferenceGroup(heading = stringResource(id = R.string.available)) {
        viewModel.availablePlugins.forEach { plugin ->
          PluginRow(plugin, false) {
            onPluginSelected(plugin)
          }
        }
      }
    }
  } else {
    NoContentScreen(label = stringResource(id = R.string.no_plugins_available))
  }
}

@Composable
private fun PluginRow(
  plugin: PluginInfo,
  installed: Boolean,
  active: Boolean = false,
  onClick: () -> Unit = {},
) {
  PreferenceTemplate(
    title = { 
      Text(
        text = plugin.title, 
        style = MaterialTheme.typography.titleMedium
      ) 
    },
    description = {
      Text(
        text = plugin.description,
        style = MaterialTheme.typography.titleSmall
      )
    },
    modifier = Modifier
      .fillMaxWidth()
      .clickable(
        onClick = {
           onClick()
           Log.e("Plugin Icon", plugin.icon!!)
        }
      ),
    startWidget = {
      AnimatedVisibility(
        visible = !plugin.icon.isNullOrEmpty(), 
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        AsyncImage(
          model = plugin.icon ?: R.drawable.android,
          contentDescription = plugin.title,
          modifier = Modifier
            .size(45.dp)
            .padding(4.dp),
          contentScale = ContentScale.Crop
        )
      }
    },
    endWidget = {
      if (installed) {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp), 
          checked = active, 
          onCheckedChange = null
        )
      }
    }
  )
}

@Composable
fun NoContentScreen(
   label: String
) {
  Column(
    modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(text = label)
  }
}

@Composable
fun AddPluginDialog(
  onDismissRequest: () -> Unit, onConfirm: () -> Unit
) {
  AlertDialog(onDismissRequest = onDismissRequest,
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
    })
}

@Composable
fun DownloadDialog(
  onDismissRequest: () -> Unit, plugin: PluginInfo, onConfirm: () -> Unit
) {
  AlertDialog(onDismissRequest = onDismissRequest,
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
    })
}

private fun getFileName(
  uri: Uri, activity: Context
): String? {
  var result: String? = null
  val cursor = activity.contentResolver.query(uri, null, null, null, null)
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