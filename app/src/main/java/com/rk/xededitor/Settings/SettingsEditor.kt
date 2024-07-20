package com.rk.xededitor.Settings

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.MainActivity.DynamicFragment
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.R
import com.rk.xededitor.databinding.ActivitySettingsMainBinding
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.onCheckedChange
import de.Maxr1998.modernpreferences.helpers.screen
import de.Maxr1998.modernpreferences.helpers.switch

class SettingsEditor : SettingsBaseActivity() {
  
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
    binding.toolbar.title = "Editor"
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
      switch("wordwrap") {
        titleRes = R.string.ww
        summary = "Enable Word Wrap in all editors"
        iconRes = R.drawable.reorder
        onCheckedChange { isChecked ->
          SettingsData.setBoolean(this@SettingsEditor, "wordwrap", isChecked)
          if (StaticData.fragments != null && StaticData.fragments.isNotEmpty()) {
            for (fragment in StaticData.fragments) {
              val dynamicFragment = fragment as DynamicFragment
              dynamicFragment.editor.isWordwrap = isChecked
            }
          }
          return@onCheckedChange true
        }
      }
      switch("keepdrawerlocked") {
        titleRes = R.string.keepdl
        summary = "Keep Drawer Locked"
        iconRes = R.drawable.lock
        onCheckedChange { isChecked ->
          SettingsData.setBoolean(this@SettingsEditor, "keepDrawerLocked", isChecked)
          return@onCheckedChange true
        }
      }
    }
    
    
  }
}