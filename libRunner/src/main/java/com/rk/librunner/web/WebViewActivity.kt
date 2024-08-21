package com.rk.librunner.web

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.rk.librunner.WebUtils
import com.rk.librunner.databinding.ActivityMarkdownBinding
import java.io.File


class WebViewActivity: AppCompatActivity()  {
	lateinit var binding:ActivityMarkdownBinding
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityMarkdownBinding.inflate(layoutInflater)
		
		setContentView(binding.root)
		
		setSupportActionBar(binding.toolbar)
		supportActionBar!!.setDisplayHomeAsUpEnabled(true)
		supportActionBar!!.setDisplayShowTitleEnabled(true)
		val file = File(intent.getStringExtra("filepath").toString())
		
		supportActionBar!!.title = file.name
		WebUtils.setupWebView(binding.webview)
		
		binding.webview.loadUrl("file://${file.absolutePath}")
		
	}
	
	
	
	override fun onLowMemory() {
		super.onLowMemory()
		System.gc()
		finish()
	}
	
	override fun onPause() {
		super.onPause()
		System.gc()
		finish()
	}
	
	override fun onDestroy() {
		super.onDestroy()
		System.gc()
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
	
	override fun onBackPressed() {
		if (binding.webview.canGoBack()) {
			binding.webview.goBack()
		} else {
			super.onBackPressed()
		}
	}
}