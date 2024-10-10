package com.rk.runner.runners.web.markdown

import android.content.Context
import android.os.Bundle
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import com.rk.runner.runners.web.HttpServer
import com.rk.runner.runners.web.WebActivity
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PORT = 8357

class MDViewer : WebActivity() {
    private lateinit var file: File
    private lateinit var httpServer: HttpServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        file = File(intent.getStringExtra("filepath").toString())

        supportActionBar!!.title = file.name

        httpServer = HttpServer(PORT, file.parentFile!!)

        // todo set dynamic background color
        // val themeColorHex = "#000"
        // <style>
        //				body{
        //					background:$themeColorHex
        //				}
        //				</style>

        /*
         * https://cdn.jsdelivr.net/npm/marked@0/marked.min.js
         * https://cdn.jsdelivr.net/npm/prismjs@1/prism.min.js
         * https://cdn.jsdelivr.net/npm/github-markdown-css@2/github-markdown.min.css
         * https://cdn.jsdelivr.net/npm/prismjs@1/themes/prism.min.css
         * */

        lifecycleScope.launch(Dispatchers.Default) {
            val sb =
                StringBuilder(
                    withContext(Dispatchers.IO) { FileInputStream(file).bufferedReader() }
                        .use { it.readText() }
                )

            val injectHtml =
                """
                <!DOCTYPE html>
			<html lang="en">
			<head>
			    <meta charset="UTF-8">
				<meta name="viewport" content="width=device-width, initial-scale=1.0">

				<script type="module">${readAssetFile(this@MDViewer,"zeromd.js")}</script>
			</head>
			<body>
				<zero-md src="/${file.name}"><zero-md>
			</body>
			</html>
                
            """
                    .trimIndent()
            sb.insert(0, injectHtml)
            withContext(Dispatchers.Main) {
                with(binding.webview) {
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(
                        "http://localhost:${PORT}",
                        sb.toString(),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            }
        }
    }

    private fun readAssetFile(context: Context, fileName: String): String {
        val assetManager = context.assets
        val inputStream = assetManager.open(fileName)
        return inputStream.bufferedReader().use { it.readText() }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (httpServer.isAlive) {
            httpServer.stop()
        }
    }
}
