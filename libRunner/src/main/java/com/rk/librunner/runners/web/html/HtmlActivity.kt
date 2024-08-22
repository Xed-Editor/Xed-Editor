package com.rk.librunner.runners.web.html

import android.content.Context
import android.os.Bundle
import com.rk.librunner.runners.WebActivity
import com.rk.librunner.runners.web.HttpServer
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

private const val PORT = 8080
class HtmlActivity : WebActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val file = File(intent.getStringExtra("filepath").toString())
		supportActionBar?.title = file.name
		
		file.parentFile?.let {
			HttpServer(PORT, it, file) { file: File?, response: StringBuilder ->
				if (file!!.name.endsWith("html")) {
					val erudaHtml = """<script>${readTextFileFromAssets(this, "eruda.js")}</script><script>eruda.init();</script>""".trimIndent()
					response.insert(0,erudaHtml+"\n")
				}
				return@HttpServer response.toString()
			}
		}
		
		binding.webview.apply {
			loadUrl("http://localhost:$PORT")
		}
	}
	
	private fun readTextFileFromAssets(context: Context, fileName: String): String {
		val assetManager = context.assets
		val inputStream = assetManager.open(fileName)
		val bufferedReader = BufferedReader(InputStreamReader(inputStream))
		return bufferedReader.use { it.readText() }
	}
}
