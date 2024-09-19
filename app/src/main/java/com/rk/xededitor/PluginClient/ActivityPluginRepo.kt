package com.rk.xededitor.PluginClient

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.databinding.ActivityPluginsBinding
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PluginViewModel : ViewModel() {
  private val plugins = mutableListOf<PluginItem>()
  fun updatePlugin(plugins: List<PluginItem>) {
    this.plugins.clear()
    this.plugins.addAll(plugins)
  }
  
  fun getPlugins(): List<PluginItem> {
    return plugins
  }
}


class ActivityPluginRepo : BaseActivity() {
  private val pluginModel: PluginViewModel by viewModels()
  lateinit var binding: ActivityPluginsBinding
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityPluginsBinding.inflate(layoutInflater)
    
    binding.fab.hide()
    binding.listView.visibility = View.GONE
    binding.scrollview.visibility = View.VISIBLE
    
    val toolbar = binding.toolbar
    setSupportActionBar(toolbar)
    supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    supportActionBar!!.title = "Plugin Repo"
    
    if (SettingsData.isDarkMode(this) && SettingsData.isOled()) {
      binding.root.setBackgroundColor(Color.BLACK)
      binding.toolbar.setBackgroundColor(Color.BLACK)
      binding.appbar.setBackgroundColor(Color.BLACK)
      window.navigationBarColor = Color.BLACK
      val window = window
      window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
      window.statusBarColor = Color.BLACK
      window.navigationBarColor = Color.BLACK
    } else if (SettingsData.isDarkMode(this)) {
      val window = window
      window.navigationBarColor = Color.parseColor("#141118")
    }
    
    setContentView(binding.root)
    
    //todo see easy componets in rooboko
    
    val xadapter = RepoPluginAdapter() { pluginItem ->
      rkUtils.toast(pluginItem.title)
    }
    
    binding.recyclerView.apply {
      adapter = xadapter
      layoutManager = LinearLayoutManager(this@ActivityPluginRepo)
      itemAnimator = null
    }
    
    if (pluginModel.getPlugins().isEmpty()) {
      val loading = LoadingPopup(this, null).setMessage("Loading plugins from repo").show()
      RepoManager.getPluginsCallback { plugins ->
        lifecycleScope.launch(Dispatchers.Main) {
          pluginModel.updatePlugin(plugins)
          xadapter.submitList(plugins)
          if (plugins.isEmpty()) {
            rkUtils.toast("Unable to load plugins")
          }
          loading.hide()
        }
      }
    }else{
      xadapter.submitList(pluginModel.getPlugins())
    }
    
    
  }
  
  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    // Handle action bar item clicks here
    val id = item.itemId
    if (id == android.R.id.home) {
      // Handle the back arrow click here
      onBackPressedDispatcher.onBackPressed()
      return true
    }
    return super.onOptionsItemSelected(item)
  }
}