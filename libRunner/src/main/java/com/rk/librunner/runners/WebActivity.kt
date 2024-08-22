package com.rk.librunner.runners

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.rk.librunner.databinding.ActivityMarkdownBinding



open class WebActivity: AppCompatActivity()  {
    lateinit var binding:ActivityMarkdownBinding
    
    @SuppressLint("SetJavaScriptEnabled")
    fun setupWebView(webview: WebView) {
        val webSettings = webview.settings
        webSettings.javaScriptEnabled = true
        webSettings.databaseEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.allowContentAccess = true
        webSettings.allowFileAccess = true
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        
        webview.setWebChromeClient(WebChromeClient());
        webview.webViewClient = WebViewClient()
        
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMarkdownBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowTitleEnabled(true)

        setupWebView(binding.webview)



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