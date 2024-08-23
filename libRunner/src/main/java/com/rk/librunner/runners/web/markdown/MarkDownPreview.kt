package com.rk.librunner.runners.web.markdown

import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import com.rk.librunner.runners.web.WebActivity
import java.io.File

private const val PORT = 8889

class MarkDownPreview : WebActivity() {
	private lateinit var httpServer: HttpServer
	private lateinit var file: File

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		file = File(intent.getStringExtra("filepath").toString())
		supportActionBar!!.title = file.name

		//todo set dynamic background color
		//val themeColorHex = "#000"
		//<style>
		//				body{
		//					background:$themeColorHex
		//				}
		//				</style>

		val html = """
			<!DOCTYPE html>
			<html lang="en">
			<head>
			    <meta charset="UTF-8">
				<meta name="viewport" content="width=device-width, initial-scale=1.0">
				
				<script type="module" src="/zeromd.js"></script>
			</head>
			<body>
				<zero-md src="/${file.name}"><zero-md>
			</body>
			</html>
		""".trimIndent()
		
		
		//its working it just need extra files
		
		/*
		* https://cdn.jsdelivr.net/npm/marked@0/marked.min.js
		* https://cdn.jsdelivr.net/npm/prismjs@1/prism.min.js
		* https://cdn.jsdelivr.net/npm/github-markdown-css@2/github-markdown.min.css
		* https://cdn.jsdelivr.net/npm/prismjs@1/themes/prism.min.css
		* */
		httpServer = object : HttpServer(PORT, file.parentFile!!, null, StringBuilder(html), null) {
			override fun serve(session: IHTTPSession?): Response {
				if (session?.uri?.contains("zeromd") == true) {
					return newFixedLengthResponse(Response.Status.OK, "text/javascript", readAssetFile(this@MarkDownPreview, "zeromd.js"))
				}
				return super.serve(session)
			}
		}
		
		
		binding.webview.webViewClient = WebViewClient()
		binding.webview.loadUrl("http://localhost:${PORT}")

	}
	
	
	fun readAssetFile(context: Context, fileName: String): String {
		val assetManager = context.assets
		val inputStream = assetManager.open(fileName)
		return inputStream.bufferedReader().use { it.readText() }
	}
	
	override fun onDestroy() {
		super.onDestroy()
		if (httpServer.isAlive){
			httpServer.stop()
		}
	}
}