package com.rk.xededitor.Settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.rk.xededitor.After
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.MainActivity.DynamicFragment
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.plugin.ManagePluginActivity.ManagePluginActivity
import kotlin.system.exitProcess


class SettingsActivity : BaseActivity() {
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.settings_activity)
    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    toolbar.setTitle(getResources().getString(R.string.title_activity_settings))
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
    
    
    var switch: MaterialSwitch? = null
    var shouldAsk: Boolean = true
    var isProgrammaticChange = false
    
    var d = MaterialAlertDialogBuilder(this@SettingsActivity)
      .setTitle(resources.getString(R.string.rr))
      .setMessage(resources.getString(R.string.rapply))
      .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
        isProgrammaticChange = true
        switch?.isChecked = !switch?.isChecked!!
      }
      .setPositiveButton(resources.getString(R.string.restart)) { _, _ ->
        SettingsData.setSetting(this@SettingsActivity, "isOled", switch?.isChecked!!.toString())
        
        doRestart(this)
        
      }
      .setOnDismissListener {
        shouldAsk = true
      }
    
    
    
    switch = Toggle(this, SettingsData.isOled(this)).setName(resources.getString(R.string.oled))
      .setDrawable(R.drawable.dark_mode)
      .setListener { _, _ ->
        if (isProgrammaticChange) {
          isProgrammaticChange = false
          return@setListener
        }
        
        if (shouldAsk) {
          shouldAsk = false
          After(210) {
            runOnUiThread {
              d.show()
            }
          }
          
        }
      }
      .showToggle().materialSwitch
    
    
    
    
    
    
    
    Toggle(this, SettingsData.getBoolean(this, "wordwrap", false)).setName(resources.getString(R.string.ww))
      .setDrawable(R.drawable.reorder).setListener { _, isChecked ->
        SettingsData.setBoolean(this@SettingsActivity, "wordwrap", isChecked)
        if (StaticData.fragments != null && StaticData.fragments.isNotEmpty()) {
          for (fragment in StaticData.fragments) {
            val dynamicFragment = fragment as DynamicFragment
            dynamicFragment.editor.isWordwrap = isChecked
          }
          //rkUtils.toast(this,"Please wait for word wrap to complete")
          
        }
      }.showToggle()
    
    Toggle(
      this,
      SettingsData.getBoolean(this, "keepDrawerLocked", false)
    ).setName(resources.getString(R.string.keepdl)).setDrawable(R.drawable.lock).setListener { _, isChecked ->
      SettingsData.setBoolean(
        this@SettingsActivity, "keepDrawerLocked", isChecked
      )
    }.showToggle()
    
    
    var switch1: MaterialSwitch? = null
    var shouldAsk1: Boolean = true
    var isProgrammaticChange1 = false
    
    var d1 = MaterialAlertDialogBuilder(this@SettingsActivity)
      .setTitle(resources.getString(R.string.rr))
      .setMessage(resources.getString(R.string.rapply))
      .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
        isProgrammaticChange1 = true
        switch1?.isChecked = !switch1?.isChecked!!
      }
      .setPositiveButton(resources.getString(R.string.restart)) { _, _ ->
        SettingsData.setBoolean(this, "enablePlugins", switch1?.isChecked!!)
        doRestart(this)
      }
      .setOnDismissListener {
        shouldAsk1 = true
      }
    
    
    
    switch1 = Toggle(this, SettingsData.getBoolean(this, "enablePlugins", false)).setName(resources.getString(R.string.plugin))
      .setDrawable(R.drawable.extension)
      .setListener { _, isChecked ->
        if (isProgrammaticChange1) {
          isProgrammaticChange1 = false
          return@setListener
        }
        
        if (shouldAsk1) {
          shouldAsk1 = false
          After(210) {
            runOnUiThread {
              d1.show()
            }
          }
        }
      }
      .showToggle().materialSwitch
    
    
    val mainBody = findViewById<LinearLayout>(R.id.mainBody)
    val v: View = LayoutInflater.from(this).inflate(R.layout.settings_activity_card, null)
    val textView = v.findViewById<TextView>(R.id.textView)
    textView.text = resources.getString(R.string.mp)
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
  
  
  private fun doRestart(c: Context?) {
    MainActivity.activity.finish()
    finishAffinity()
    exitProcess(0)
  }
  
  
}