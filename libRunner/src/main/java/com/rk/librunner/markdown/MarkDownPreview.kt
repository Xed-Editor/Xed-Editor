package com.rk.librunner.markdown

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.rk.librunner.databinding.ActivityMarkdownBinding
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader


class MarkDownPreview : AppCompatActivity()  {
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
		setupWebView()
		
		val html = """
			<!DOCTYPE html>
			<html lang="en">
			<head>
			    <meta charset="UTF-8">
				<script type="module" src="file:///android_asset/zeromd.js"></script>
			</head>
			<body>
				<zero-md src="file://${file.absolutePath}"><zero-md>
			</body>
			</html>
		""".trimIndent()
		
		
		val baseUrl = "file://${file.parentFile!!.absolutePath}"
		
		binding.webview.loadDataWithBaseURL(baseUrl,html,"text/html", "UTF-8", null)
		
		
	}
	
	@SuppressLint("SetJavaScriptEnabled")
	private fun setupWebView() {
		val webSettings = binding.webview.settings
		webSettings.javaScriptEnabled = true
		webSettings.databaseEnabled = true
		webSettings.domStorageEnabled = true
		webSettings.javaScriptCanOpenWindowsAutomatically = true
		webSettings.allowContentAccess = true
		webSettings.allowFileAccess = true
		webSettings.allowFileAccessFromFileURLs = true
		webSettings.allowUniversalAccessFromFileURLs = true
		webSettings.cacheMode = WebSettings.LOAD_DEFAULT;
		
		binding.webview.setWebChromeClient(WebChromeClient());
		binding.webview.webViewClient = WebViewClient()
		
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
	
	
	fun readFileFromAssets(context: Context, fileName: String?): String {
		val stringBuilder = StringBuilder()
		val assetManager = context.assets
		
		try {
			assetManager.open(fileName!!).use { inputStream ->
				BufferedReader(InputStreamReader(inputStream)).use { reader ->
					var line: String?
					while ((reader.readLine().also { line = it }) != null) {
						stringBuilder.append(line).append("\n")
					}
				}
			}
		} catch (e: IOException) {
			e.printStackTrace()
		}
		
		return stringBuilder.toString()
	}
	
}