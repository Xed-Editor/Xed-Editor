package com.rk.xededitor.Settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.WindowManager
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.After
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.LoadingPopup
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.databinding.ActivitySettingsMainBinding
import com.rk.xededitor.plugin.ManagePluginActivity.ManagePluginActivity
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.onCheckedChange
import de.Maxr1998.modernpreferences.helpers.onClickView
import de.Maxr1998.modernpreferences.helpers.pref
import de.Maxr1998.modernpreferences.helpers.screen
import de.Maxr1998.modernpreferences.helpers.switch

class SettingsApp : BaseActivity() {
  private lateinit var recyclerView: RecyclerView
  private lateinit var binding: ActivitySettingsMainBinding
  private lateinit var padapter: PreferencesAdapter
  private lateinit var playoutManager: LinearLayoutManager
  
  fun get_recycler_view(): RecyclerView {
    binding = ActivitySettingsMainBinding.inflate(layoutInflater)
    recyclerView = binding.recyclerView
    return recyclerView
  }
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    padapter = PreferencesAdapter(getScreen())
    
    
    savedInstanceState?.getParcelable<PreferencesAdapter.SavedState>("padapter")
      ?.let(padapter::loadSavedState)
    
    playoutManager = LinearLayoutManager(this)
    get_recycler_view().apply {
      layoutManager = playoutManager
      adapter = padapter
      //layoutAnimation = AnimationUtils.loadLayoutAnimation(this@settings2, R.anim.preference_layout_fall_down)
    }
    
    setContentView(binding.root)
    binding.toolbar.title = "Application"
    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    
    if (SettingsData.isDarkMode(this) && SettingsData.isOled(this)) {
      binding.root.setBackgroundColor(Color.BLACK)
      binding.toolbar.setBackgroundColor(Color.BLACK)
      binding.appbar.setBackgroundColor(Color.BLACK)
      window.navigationBarColor = Color.BLACK
      val window = window
      window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
      window.statusBarColor = Color.BLACK
      window.navigationBarColor = Color.BLACK
    }
  }
  
  private fun getScreen(): PreferenceScreen {
    return screen(this) {
      switch("oled") {
        titleRes = R.string.oled
        summary = "Pure Black theme for amoled devices"
        iconRes = R.drawable.dark_mode
        defaultValue = false
        onCheckedChange { newValue ->
          SettingsData.setBoolean(this@SettingsApp, "isOled", newValue)
          LoadingPopup(this@SettingsApp, 180)
          MainActivity.activity?.recreate()
          return@onCheckedChange true
        }
        
      }
      switch("plugin") {
        titleRes = R.string.plugin
        summary = "Enable/Disable Plugins"
        iconRes = R.drawable.extension
        onCheckedChange { newValue ->
          SettingsData.setBoolean(this@SettingsApp, "enablePlugins", newValue)
          
          val loading = LoadingPopup(this@SettingsApp,null).show()
          After(800){
            runOnUiThread {
              padapter.setRootScreen(getScreen())
              MainActivity.activity?.recreate()
              loading.hide()
            }
          }
          
          //SettingsMainActivity.settingsMain?.recreate()
          return@onCheckedChange true
        }
      }
      if (SettingsData.getBoolean(this@SettingsApp, "enablePlugins", false)) {
        pref("ManagePlugins") {
          title = "Manage Plugins"
          summary = "Enable/Disable installed plugins"
          iconRes = R.drawable.extension
          onClickView {
            startActivity(Intent(this@SettingsApp, ManagePluginActivity::class.java))
          }
        }
      }
      
    }
  }
  
  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    // Save the padapter state as a parcelable into the Android-managed instance state
    outState.putParcelable("padapter", padapter.getSavedState())
  }
  
  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    // Handle action bar item clicks here
    val id = item.itemId
    if (id == android.R.id.home) {
      // Handle the back arrow click here
      onBackPressed()
      return true
    }
    return super.onOptionsItemSelected(item)
  }
}