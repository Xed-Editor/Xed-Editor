package com.rk.xededitor.PluginClient

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import com.rk.librunner.runners.web.HttpServer
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.databinding.ActivityPluginInfoBinding
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.http2.Settings

private const val PORT=8839
class PluginInfo : BaseActivity() {
  lateinit var  binding: ActivityPluginInfoBinding
  lateinit var plugin:PluginItem
  lateinit var httpServer: HttpServer
  lateinit var rawUrl:String
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityPluginInfoBinding.inflate(layoutInflater)
    setContentView(binding.root)
    setSupportActionBar(binding.toolbar)
    supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    supportActionBar!!.setDisplayShowTitleEnabled(true)
    
    @Suppress("DEPRECATION")
    plugin = intent.getSerializableExtra("plugin") as PluginItem
    rawUrl = RepoManager.getRawGithubUrl(plugin.repo)+"/main"
    supportActionBar!!.title = plugin.title
    
    setupWebView(binding.readme)
    
    //todo
    lifecycleScope.launch(Dispatchers.IO){
      val html = """
       <!DOCTYPE html>
			<html lang="en">
			<head>
			    <meta charset="UTF-8">
				<meta name="viewport" content="width=device-width, initial-scale=1.0">
				
				<script type="module">${rkUtils.readAssetFile(this@PluginInfo,"zeromd.js")}</script>
			</head>
			<body>
				<zero-md src="$rawUrl/README.md"><zero-md>
			</body>
			</html>
            
            """.trimIndent()
      withContext(Dispatchers.Main){
        binding.readme.loadDataWithBaseURL(rawUrl,html,"text/html","utf-8",null)
      }
    }
    
    
    
    
  }
  
  //todo
  override fun onBackPressed() {
    if (binding.readme.canGoBack()) {
      binding.readme.goBack()
    } else {
      super.onBackPressed()
    }
  }
  
  @SuppressLint("SetJavaScriptEnabled")
  fun setupWebView(webView: WebView) {
    val webSettings = webView.settings
    webSettings.javaScriptEnabled = true
    webSettings.databaseEnabled = true
    webSettings.domStorageEnabled = true
    webSettings.javaScriptCanOpenWindowsAutomatically = true
    webSettings.allowContentAccess = true
    webSettings.allowFileAccess = true
    webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    webView.setWebChromeClient(WebChromeClient());
    
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