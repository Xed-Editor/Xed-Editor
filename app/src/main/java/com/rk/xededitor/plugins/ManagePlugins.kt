package com.rk.xededitor.plugins

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libPlugin.server.Plugin
import com.rk.libPlugin.server.PluginUtils
import com.rk.libPlugin.server.PluginUtils.getPluginRoot
import com.rk.libPlugin.server.PluginUtils.indexPlugins
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.theme.KarbonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import java.io.File
import java.io.Serializable

class PluginItem(
  val icon: String?,
  val title: String,
  val packageName: String,
  val description: String,
  val versionCode: Int,
  val repo: String
) : Serializable


class PluginModel : ViewModel() {
  var availablePlugins = listOf<PluginItem>()
  var isLoading by mutableStateOf(false)

  init {
    if (availablePlugins.isEmpty()) {
      isLoading = true
      RepoManager.getPluginsCallback { plugins ->
        viewModelScope.launch(Dispatchers.Main) {
          availablePlugins = plugins
          isLoading = false
        }
      }
    }
  }
}

class ManagePlugins : ComponentActivity() {
  private val model: PluginModel by viewModels()

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      KarbonTheme {
        Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
          TopAppBar(title = { Text(text = "Manage Plugins") }, navigationIcon = {
            IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
              )
            }
          })
        }) { innerPadding ->
          var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
          val tabs = listOf("Installed", "Available")

          Column {
            TabRow(
              selectedTabIndex = selectedTabIndex,
              modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
            ) {
              tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTabIndex == index,
                  onClick = { selectedTabIndex = index },
                  text = { Text(title) })
              }
            }

            Crossfade(targetState = selectedTabIndex, label = "screens") { screen ->
              when (screen) {
                0 -> Installed()
                1 -> Available(viewModel = model)
              }
            }
          }

        }
      }
    }
  }

  @Composable
  fun Installed() {
    var plugins by rememberSaveable { mutableStateOf(listOf<Plugin>()) }

    LaunchedEffect(Unit) {
      withContext(Dispatchers.Default) {
        application.indexPlugins()
        val plugins1 = PluginUtils.getInstalledPlugins()
        if ((plugins1 == plugins).not()) {
          withContext(Dispatchers.Main) {
            plugins = plugins1
          }
        }

      }
    }

    LazyColumn {
      items(plugins) { plugin ->
        val title = plugin.info.name
        val packageName = plugin.info.packageName
        val icon = plugin.info.icon

        var active by rememberSaveable {
          mutableStateOf(
            PluginUtils.isPluginActive(
              this@ManagePlugins, plugin.info.packageName, false
            )
          )
        }

        ElevatedCard(
          modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
          onClick = {
            active = active.not()
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
              lifecycleScope.launch(Dispatchers.Default) {
                PluginUtils.setPluginActive(
                  this@ManagePlugins, plugin.info.packageName, checked
                )
              }
            })

          }
        }
      }
    }
  }

  @Composable
  fun Available(viewModel: PluginModel) {
    if (viewModel.isLoading) {
      // Display CircularProgressIndicator while loading
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
      ) {
        CircularProgressIndicator()
      }
    } else {
      // Show the plugins once they are loaded
      LazyColumn {
        items(viewModel.availablePlugins) { plugin ->
          val title = plugin.title

          ElevatedCard(
            modifier = Modifier
              .fillMaxWidth()
              .padding(8.dp),
            onClick = {
              lifecycleScope.launch(Dispatchers.Default) {
                application.indexPlugins()
                if (PluginUtils.getInstalledPlugins().map { it.info.packageName }
                    .contains(plugin.packageName)) {
                  withContext(Dispatchers.Main) {
                    rkUtils.toast("Already Installed")
                  }
                } else {
                  MaterialAlertDialogBuilder(this@ManagePlugins).setTitle("Download")
                    .setMessage("Are you sure you want to download ${plugin.title}?").setNegativeButton("Cancel", null)
                    .setPositiveButton("Yes") { _, _ ->
                      val loading =
                        LoadingPopup(this@ManagePlugins, null).setMessage("Downloading plugin")
                          .show()
                      lifecycleScope.launch(Dispatchers.IO) {
                        try {
                          Git.cloneRepository().setURI(plugin.repo)
                            .setDirectory(File(getPluginRoot(), plugin.title)).setBranch("main")
                            .call()
                          withContext(Dispatchers.Main) {
                            loading.hide()
                            rkUtils.toast("Successfully Downloaded.")
                          }
                        } catch (e: Exception) {
                          e.printStackTrace()
                          withContext(Dispatchers.Main) {
                            loading.hide()
                            rkUtils.toast("Unable to downloaded plugin : ${e.message}")
                          }

                        }
                      }
                    }.show()
                }
              }

            },
          ) {
            Row(modifier = Modifier.padding(8.dp)) {
              AsyncImage(
                model = plugin.icon,
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
                  text = plugin.description, style = MaterialTheme.typography.bodySmall
                )
              }
            }
          }
        }
      }
    }
  }


}
