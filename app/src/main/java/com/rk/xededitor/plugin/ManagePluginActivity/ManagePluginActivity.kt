package com.rk.xededitor.plugin.ManagePluginActivity

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.divider.MaterialDivider
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.Data.activity
import com.rk.xededitor.R
import com.rk.xededitor.databinding.ActivityManagePluginBinding
import com.rk.xededitor.plugin.PluginServer
import com.rk.xededitor.rkUtils

class ManagePluginActivity : BaseActivity() {
  lateinit var binding: ActivityManagePluginBinding
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityManagePluginBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.toolbar.title = "Manage Plugins"
    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayShowTitleEnabled(true)
    supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    
    
    val v: View = LayoutInflater.from(this).inflate(R.layout.settings_activity_card, null)
    val textView = v.findViewById<TextView>(R.id.textView)
    textView.text = "Available Plugins"
    val imageView = v.findViewById<ImageView>(R.id.imageView)
    imageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.extension))
    
    
    val view = View(this)
    val layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 25)
    view.layoutParams = layoutParams
    
    binding.mainBody.addView(view)
    binding.mainBody.addView(v)
    
    val view2 = View(this)
    view2.layoutParams = layoutParams
    
    binding.mainBody.addView(view2)
    
    val divider = MaterialDivider(this)
    
    
    binding.mainBody.addView(divider)
    
    v.setOnClickListener {
      rkUtils.ni(this)
    }
    
    
    val view3 = View(this)
    view3.layoutParams = layoutParams
    
    val listView = NonScrollListView(this)
    listView.adapter = listAdapter(
      this, PluginServer.arrayOfPluginNames, PluginServer.arrayOfPluginPackageNames,
      PluginServer.arrayOfPluginIcons
    )
    
    
    binding.mainBody.addView(view3)
    if(PluginServer.arrayOfPluginNames.isEmpty()){
      val textView = TextView(activity)
      textView.text = "No Installed Plugins"
      textView.gravity = Gravity.CENTER
      binding.mainBody.addView(textView)
    }else{
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