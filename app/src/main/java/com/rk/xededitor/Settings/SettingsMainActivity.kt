package com.rk.xededitor.Settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.R
import com.rk.xededitor.databinding.ActivitySettingsMainBinding
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.onClickView
import de.Maxr1998.modernpreferences.helpers.pref
import de.Maxr1998.modernpreferences.helpers.screen

class SettingsMainActivity : SettingsBaseActivity() {
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
    binding.toolbar.title = "Settings"
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
      pref("Application") {
        title = "Application"
        summary = "General settings for the application"
        iconRes = R.drawable.android
        onClickView {
          startActivity(Intent(this@SettingsMainActivity, SettingsApp::class.java))
        }
      }
      pref("editor") {
        title = "Editor"
        summary = "General settings for the editor"
        iconRes = R.drawable.edit
        onClickView {
          startActivity(Intent(this@SettingsMainActivity, SettingsEditor::class.java))
        }
      }
    }
  }
  
  
}