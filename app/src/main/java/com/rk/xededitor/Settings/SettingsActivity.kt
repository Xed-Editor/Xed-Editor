package com.rk.xededitor.Settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.Data
import com.rk.xededitor.MainActivity.DynamicFragment
import com.rk.xededitor.R
import com.rk.xededitor.plugin.ManagePluginActivity.ManagePluginActivity
import com.rk.xededitor.rkUtils


class SettingsActivity : BaseActivity() {
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.settings_activity)
    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    toolbar.setTitle("Settings")
    setSupportActionBar(toolbar)
    
    supportActionBar?.setDisplayShowTitleEnabled(true)
    supportActionBar!!.setDisplayHomeAsUpEnabled(true) // for add back arrow in action bar
    val actionBar = supportActionBar
    actionBar?.setDisplayHomeAsUpEnabled(true)
    if (!SettingsData.isDarkMode(this)) {
      //light mode
      window.navigationBarColor = Color.parseColor("#FEF7FF")
      val decorView = window.decorView
      var flags = decorView.systemUiVisibility
      flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
      decorView.systemUiVisibility = flags
    }
    if (SettingsData.isDarkMode(this) && SettingsData.isOled(this)) {
      findViewById<View>(R.id.drawer_layout).setBackgroundColor(Color.BLACK)
      findViewById<View>(R.id.appbar).setBackgroundColor(Color.BLACK)
      findViewById<View>(R.id.toolbar).setBackgroundColor(Color.BLACK)
      window.navigationBarColor = Color.BLACK
      val window = window
      window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
      window.statusBarColor = Color.BLACK
    }
    
    
    
    
    Toggle(this, SettingsData.isOled(this)).setName("Black Night Theme")
      .setDrawable(R.drawable.dark_mode).setListener { _, isChecked ->
        SettingsData.addToapplyPrefsOnRestart(
          this@SettingsActivity, "isOled", isChecked.toString()
        )
        rkUtils.toast(this@SettingsActivity, "Setting will take effect after restart")
      }.showToggle()
    
    
    
    Toggle(this, SettingsData.getBoolean(this, "wordwrap", false)).setName("Word wrap")
      .setDrawable(R.drawable.reorder).setListener { _, isChecked ->
        SettingsData.setBoolean(this@SettingsActivity, "wordwrap", isChecked)
        if (Data.fragments != null && Data.fragments.isNotEmpty()) {
          for (fragment in Data.fragments) {
            val dynamicFragment = fragment as DynamicFragment
            dynamicFragment.editor.isWordwrap = isChecked
          }
          //rkUtils.toast(this,"Please wait for word wrap to complete")
          
        }
      }.showToggle()
    
    Toggle(
      this,
      SettingsData.getBoolean(this, "keepDrawerLocked", false)
    ).setName("Keep Drawer Locked").setDrawable(R.drawable.lock).setListener { _, isChecked ->
      SettingsData.setBoolean(
        this@SettingsActivity, "keepDrawerLocked", isChecked
      )
    }.showToggle()
    
    
    
    Toggle(this, SettingsData.getBoolean(this, "enablePlugins", false))
      .setName("Plugins")
      .setDrawable(R.drawable.extension)
      .setListener { _, isChecked ->
        rkUtils.toast(this, "Setting will take effect after restart")
        SettingsData.setBoolean(this, "enablePlugins", isChecked)
      }.showToggle()
    
    val mainBody = findViewById<LinearLayout>(R.id.mainBody)
    val v: View = LayoutInflater.from(this).inflate(R.layout.settings_activity_card, null)
    val textView = v.findViewById<TextView>(R.id.textView)
    textView.text = "Manage Plugins"
    val imageView = v.findViewById<ImageView>(R.id.imageView)
    imageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.extension))
    if (SettingsData.getBoolean(this, "enablePlugins", false)) v.visibility =
      View.VISIBLE else v.visibility = View.GONE
    
    
    val view = View(this)
    
    val layoutParams = RelativeLayout.LayoutParams(
      RelativeLayout.LayoutParams.MATCH_PARENT, // Width
      50
    )
    view.layoutParams = layoutParams
    
    
    
    
    
    
    
    
    mainBody.addView(view)
    mainBody.addView(v)
    
    v.setOnClickListener {
      val intent = Intent(this, ManagePluginActivity::class.java)
      startActivity(intent)
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