package com.rk.xededitor.PluginClient

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rk.libPlugin.server.PluginInstaller
import com.rk.libPlugin.server.PluginUtils.getPluginRoot
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.databinding.ActivityPluginsBinding
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import java.io.File

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
    
    val xadapter = RepoPluginAdapter({pluginItem ->
      //whole item clciked
      startActivity(Intent(this,PluginInfo::class.java).also {
        it.putExtra("plugin",pluginItem)
      })
    }) { pluginItem,button ->
      //download button clicked
      val loading = LoadingPopup(this, null).setMessage("Downloading plugin").show()
      lifecycleScope.launch(Dispatchers.IO){
        try {
          Git.cloneRepository().setURI(pluginItem.repo).setDirectory(File(getPluginRoot(),pluginItem.title)).setBranch("main")
            .call()
          withContext(Dispatchers.Main) {
            loading.hide()
            ManagePlugin.activityRef.get()?.recreate()
            button.visibility = View.GONE
            rkUtils.toast("Successfully Downloaded.")
          }
        } catch (e: Exception) {
          e.printStackTrace()
          withContext(Dispatchers.Main){
            loading.hide()
            rkUtils.toast("Unable to downloaded plugin : ${e.message}")
          }
       
        }
        
      }
    }
    
    binding.recyclerView.apply {
      adapter = xadapter
      layoutManager = LinearLayoutManager(this@ActivityPluginRepo)
      itemAnimator = null
    }
    
    ManagePlugin.activityRef.get()?.let {
      if (it.pluginModel.getPlugins().isEmpty()) {
        val loading = LoadingPopup(this, null).setMessage("Loading plugins from repo").show()
        RepoManager.getPluginsCallback { plugins ->
          lifecycleScope.launch(Dispatchers.Main) {
            it.pluginModel.updatePlugin(plugins)
            xadapter.submitList(plugins)
            if (plugins.isEmpty()) {
              rkUtils.toast("Unable to load plugins")
            }
            loading.hide()
          }
        }
      }else{
        xadapter.submitList(it.pluginModel.getPlugins())
      }
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