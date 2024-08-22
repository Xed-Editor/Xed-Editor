package com.rk.librunner.runners.web.markdown

import android.os.Bundle
import com.rk.librunner.runners.WebActivity
import com.rk.librunner.runners.web.HttpServer
import java.io.File

private const val PORT = 8889
class MarkDownPreview : WebActivity(){
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val file = File(intent.getStringExtra("filepath").toString())
		supportActionBar!!.title = file.name
		
		
		
		
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
		
		file.parentFile?.let { HttpServer(PORT, it,null,StringBuilder(html),null) }
		
		
		binding.webview.loadUrl("http://localhost:${PORT}")
	}
}