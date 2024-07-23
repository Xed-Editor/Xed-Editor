package com.rk.xededitor.Settings

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.After
import com.rk.xededitor.LoadingPopup
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.databinding.ActivitySettingsMainBinding
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.onCheckedChange
import de.Maxr1998.modernpreferences.helpers.screen
import de.Maxr1998.modernpreferences.helpers.switch

class SettingsApp : SettingsBaseActivity() {
  private lateinit var recyclerView: RecyclerView
  private lateinit var binding: ActivitySettingsMainBinding
  
  override fun get_recycler_view(): RecyclerView {
    binding = ActivitySettingsMainBinding.inflate(layoutInflater)
    recyclerView = binding.recyclerView
    return recyclerView
  }
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
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
  
  
  override fun getScreen(): PreferenceScreen {
    return screen(this) {
      switch("oled") {
        titleRes = R.string.oled
        summary = "Pure Black theme for amoled devices"
        iconRes = R.drawable.dark_mode
        defaultValue = true
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
          LoadingPopup(this@SettingsApp, 180)
          MainActivity.activity?.recreate()
          SettingsMainActivity.settingsMain?.recreate()
          return@onCheckedChange true
        }
      }
      
    }
  }
}