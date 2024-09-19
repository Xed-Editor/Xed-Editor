package com.rk.xededitor.PluginClient

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.R
import com.rk.xededitor.databinding.ActivityPluginInfoBinding
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class PluginInfo : BaseActivity() {
  lateinit var  binding: ActivityPluginInfoBinding
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityPluginInfoBinding.inflate(layoutInflater)
    setContentView(binding.root)
    setSupportActionBar(binding.toolbar)
    supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    supportActionBar!!.setDisplayShowTitleEnabled(true)
    val pluginItem:PluginItem = intent.getSerializableExtra("plugin") as PluginItem
    supportActionBar!!.title = pluginItem.title
    
  }
  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    // Handle action bar item clicks here
    val id = item.itemId
    
    when (id) {
      android.R.id.home -> {
        onBackPressed()
        return true
      }
    }
    
    
    return super.onOptionsItemSelected(item)
  }
}