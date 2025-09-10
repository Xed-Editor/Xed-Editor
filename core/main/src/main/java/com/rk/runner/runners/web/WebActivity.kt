package com.rk.runner.runners.web

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.rk.xededitor.ui.theme.KarbonTheme

@OptIn(ExperimentalMaterial3Api::class)
abstract class WebActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    fun setupWebView(webView: WebView) {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.databaseEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.webChromeClient = WebChromeClient()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            KarbonTheme {
                WebScreen(
                    title = "WebView",
                    onBackPressed = { handleBackPressed() },
                    setupWebView = { setupWebView(it) }
                )
            }

        }
    }

    private fun handleBackPressed() {
        val webView = findViewById<WebView>(android.R.id.content).findViewById<WebView>(0)
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        System.gc()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        System.gc()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(
    title: String,
    onBackPressed: () -> Unit,
    setupWebView: (WebView) -> Unit
) {
    var webView: WebView? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    setupWebView(this)
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
            update = { }
        )
    }
}
