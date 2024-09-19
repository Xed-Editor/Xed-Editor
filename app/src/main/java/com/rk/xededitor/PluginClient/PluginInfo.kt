package com.rk.xededitor.PluginClient

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.R
import com.rk.xededitor.databinding.ActivityPluginInfoBinding

class PluginInfo : BaseActivity() {
  lateinit var  binding: ActivityPluginInfoBinding
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityPluginInfoBinding.inflate(layoutInflater)
    setContentView(binding.root)
    setSupportActionBar(binding.toolbar)
    supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    supportActionBar!!.setDisplayShowTitleEnabled(true)
    
  }
}