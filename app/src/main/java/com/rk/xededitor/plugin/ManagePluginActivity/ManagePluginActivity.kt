package com.rk.xededitor.plugin.ManagePluginActivity

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.divider.MaterialDivider
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.databinding.ActivityManagePluginBinding
import com.rk.xededitor.plugin.PluginServer
import com.rk.xededitor.rkUtils

class ManagePluginActivity : BaseActivity() {
  lateinit var binding: ActivityManagePluginBinding
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityManagePluginBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.toolbar.title = resources.getString(R.string.mp)
    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayShowTitleEnabled(true)
    supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    
    if (SettingsData.isDarkMode(this) && SettingsData.isOled(this)) {
      binding.appbar.setBackgroundColor(Color.BLACK)
      binding.toolbar.setBackgroundColor(Color.BLACK)
      binding.mainBody.setBackgroundColor(Color.BLACK)
      binding.root.setBackgroundColor(Color.BLACK)
      val window = window
      window.navigationBarColor = Color.BLACK
      window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
      window.statusBarColor = Color.BLACK
    }
    
    if (PluginServer.arrayOfPluginNames.isNotEmpty()) {
      binding.nip.visibility = View.GONE
      val listView = NonScrollListView(this)
      listView.adapter = listAdapter(
        this, PluginServer.arrayOfPluginNames, PluginServer.arrayOfPluginPackageNames,
        PluginServer.arrayOfPluginIcons
      )
      binding.mainBody.addView(listView)
    }
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
