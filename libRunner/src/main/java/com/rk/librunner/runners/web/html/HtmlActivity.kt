package com.rk.librunner.runners.web.html

import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import com.rk.librunner.runners.web.WebActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

private const val PORT = 8080

//random characters for javascript variable name
private const val C = "ceskhbfbdnjjdd"

class HtmlActivity : WebActivity() {
    private lateinit var file: File
    private lateinit var httpServer: HttpServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        file = File(intent.getStringExtra("filepath").toString())

        httpServer = HttpServer(PORT, file.parentFile!!)


        lifecycleScope.launch(Dispatchers.Default){
            val sb = StringBuilder(withContext(Dispatchers.IO) {
                FileInputStream(file).bufferedReader()
            }.use { it.readText() })
            val erudaJs = readAssetFile(this@HtmlActivity, "eruda.js")
            val js =
                """eruda.init();eruda.remove('elements');eruda.remove('network');eruda.remove('sources');eruda.remove('snippets');let $C = eruda.get('console');$C.config.set('catchGlobalErr',true);$C.config.set('overrideConsole',true);$C.config.set('displayExtraInfo',true);""".trimIndent()
            val injectHtml = """
                <script>$erudaJs</script>
                <script>$js</script>
            """.trimIndent()
            sb.insert(0, injectHtml)
            withContext(Dispatchers.Main){
                with(binding.webview) {
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            with(binding.webview.title) {
                                if (isNullOrEmpty().not()) {
                                    supportActionBar!!.title = this
                                } else {
                                    supportActionBar!!.title = file.name
                                }
                            }
                            super.onPageFinished(view, url)
                        }
                    }
                    loadDataWithBaseURL(
                        "http://localhost:$PORT",
                        sb.toString(),
                        "text/html",
                        "utf-8",
                        null
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
