package com.rk.runner.runners.web

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rk.xededitor.databinding.ActivityMarkdownBinding

abstract class WebActivity : AppCompatActivity() {
    var binding: ActivityMarkdownBinding? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun setupWebView(webView: WebView) {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.databaseEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        //webSettings.allowContentAccess = true
        //webSettings.allowFileAccess = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.setWebChromeClient(WebChromeClient())
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMarkdownBinding.inflate(layoutInflater)

        setContentView(binding!!.root)

        setSupportActionBar(binding!!.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowTitleEnabled(true)

        setupWebView(binding!!.webview)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        System.gc()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
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
        if (binding!!.webview.canGoBack()) {
            binding!!.webview.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
