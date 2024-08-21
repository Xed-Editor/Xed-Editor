package com.rk.librunner

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

object WebUtils {
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
		webSettings.cacheMode = WebSettings.LOAD_NO_CACHE;
		
		webview.setWebChromeClient(WebChromeClient());
		webview.webViewClient = WebViewClient()
		
	}
}