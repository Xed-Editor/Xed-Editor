package com.rk.xededitor.plugin

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.rk.libPlugin.server.Plugin
import com.rk.libPlugin.server.PluginUtils
import com.rk.libPlugin.server.PluginUtils.indexPlugins

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    suspend fun loadInstalledPlugins(app: Application) {
        withContext(Dispatchers.Default){
            app.indexPlugins()
            viewModelScope.launch(Dispatchers.IO) {
                val installedPlugins = PluginUtils.getInstalledPlugins()
                plugins = installedPlugins
            }
        }
    }
}