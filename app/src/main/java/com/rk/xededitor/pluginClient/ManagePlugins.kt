package com.rk.xededitor.pluginClient

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
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
import com.rk.libPlugin.server.PluginInstaller
import com.rk.libPlugin.server.PluginUtils
import com.rk.libPlugin.server.PluginUtils.getPluginRoot
import com.rk.libPlugin.server.PluginUtils.indexPlugins
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.theme.KarbonTheme
import com.rk.xededitor.BaseActivity
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
  val repo: String
) : Serializable


class PluginModel : ViewModel() {
  var plugins by mutableStateOf(listOf<Plugin>())
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

  // Load installed plugins into the plugins state
  suspend fun loadInstalledPlugins(app:Application) {
    withContext(Dispatchers.Default){
      app.indexPlugins()
      viewModelScope.launch(Dispatchers.IO) {
        val installedPlugins = PluginUtils.getInstalledPlugins()
        plugins = installedPlugins
      }
    }
  }
}


class ManagePlugins : BaseActivity() {
  private val model: PluginModel by viewModels()

  private val PICK_FILE_REQUEST_CODE = 46547


  @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    fun getFileName(uri: Uri): String? {
      var result: String? = null
      val cursor = contentResolver.query(uri, null, null, null, null)
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
    if (resultCode == RESULT_OK && requestCode == PICK_FILE_REQUEST_CODE) {
      data?.data?.let { uri ->
        val fileName = getFileName(uri).toString()
        if (fileName.endsWith(".zip").not()) {
          rkUtils.toast(rkUtils.getString(R.string.invalid_file_type))
          return
        }
        val loading = LoadingPopup(this, null).show()
        lifecycleScope.launch(Dispatchers.IO) {
          val isInstalled = contentResolver.openInputStream(uri)
            ?.let { PluginInstaller.installFromZip(this@ManagePlugins, it) } ?: false

          withContext(Dispatchers.Main) {
            if (isInstalled) {
              rkUtils.toast(rkUtils.getString(R.string.install_done))
              model.loadInstalledPlugins(application)
            } else {
              rkUtils.toast(rkUtils.getString(R.string.install_failed))
            }
            loading.hide()
          }

        }


      }
    }

  }

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      KarbonTheme {
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          topBar = {
            TopAppBar(title = { Text(text = getString(R.string.manage_plugins)) },
              navigationIcon = {
                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                  )
                }
              })
          },
          floatingActionButton = {
            FloatingActionButton(
              onClick = {
                MaterialAlertDialogBuilder(this@ManagePlugins).setTitle("${rkUtils.getString(R.string.add)} ${rkUtils.getString(R.string.plugin)}")
                  .setMessage(rkUtils.getString(R.string.choose_zip))
                  .setNegativeButton(rkUtils.getString(R.string.cancel), null).setPositiveButton(rkUtils.getString(R.string.choose_file)) { dialog, which ->
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "*/*"
                    startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
                  }.show()
              },
              modifier = Modifier.padding(8.dp),
            ) {
              Icon(Icons.Filled.Add, "Floating action button.")
            }
          },
          floatingActionButtonPosition = FabPosition.End,
        ) { innerPadding ->
          var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
          val tabs = listOf(getString(R.string.installed), getString(R.string.available))



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
                0 -> Installed(viewModel = model)
                1 -> Available(viewModel = model)
              }
            }
          }


        }
      }
    }
  }

  @Composable
  fun Installed(viewModel: PluginModel) {
    val plugins = viewModel.plugins

    LaunchedEffect(Unit) {
      viewModel.loadInstalledPlugins(application)
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
                    rkUtils.toast(getString(R.string.already_installed))
                  }
                } else {
                  withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(this@ManagePlugins).setTitle(
                      getString(R.string.download)
                    ).setMessage("${getString(R.string.download_sure)} ${plugin.title}?")
                      .setNegativeButton(getString(R.string.cancel), null)
                      .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        val loading = LoadingPopup(
                          this@ManagePlugins, null
                        ).setMessage(getString(R.string.downloading_plugin)).show()
                        lifecycleScope.launch(Dispatchers.IO) {
                          try {
                            Git.cloneRepository().setURI(plugin.repo).setDirectory(
                              File(
                                getPluginRoot(), plugin.title
                              )
                            ).setBranch("main").call()
                            withContext(Dispatchers.Main) {
                              loading.hide()
                              rkUtils.toast(getString(R.string.download_done))
                            }
                          } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                              loading.hide()
                              rkUtils.toast("${getString(R.string.plugin_download_failed)} : ${e.message}")
                            }

                          }
                        }
                      }.show()
                  }
                }
              }

            },
          ) {
            Row(modifier = Modifier.padding(8.dp)) {
              AsyncImage(
                model = plugin.icon,
                contentDescription = getString(R.string.plugin_icon),
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
