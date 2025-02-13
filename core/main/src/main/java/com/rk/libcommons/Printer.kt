package com.rk.libcommons

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebViewClient
import org.davidmoten.text.utils.WordWrap

@SuppressLint("SetJavaScriptEnabled")
class Printer(private val context:Context) {
    private val webView = WebView(context).apply {
        val webSettings = settings
        webSettings.javaScriptEnabled = true
        webSettings.databaseEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setWebChromeClient(WebChromeClient())
    }
    
    fun setHtml(htmlText:String):Printer{
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                print()
            }
        }
        webView.loadDataWithBaseURL(null, htmlText, "text/html", "UTF-8", null)
        return this
    }

    fun setMarkDown(md:String){
        val htmlBuilder = StringBuilder("<!DOCTYPE html>\n" +
                "        <html>\n" +
                "        <head>\n" +
                "            <script type=\"module\" src=\"https://cdn.jsdelivr.net/npm/zero-md@3?register\"></script>\n" +
                "        </head>\n" +
                "        <style>" +
                "        </style>" +
                "        <body>\n" +
                "             <zero-md>\n" +
                "             <script type=\"text/markdown\">")
        htmlBuilder.append(md)
        htmlBuilder.append("</script>\n" +
                "             </zero-md>\n" +
                "        </body>\n" +
                "        </html>")

        setHtml(htmlBuilder.toString())
    }

    fun setCodeText(code:String,language:String = "txt"){
        val text = WordWrap.from(code).maxWidth(88).wrap()
        setMarkDown("```$language \n$text\n```")
    }

    private fun print(){
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "WebView PDF Document"
        val adapter = webView.createPrintDocumentAdapter(jobName)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        printManager.print(jobName, adapter, attributes)
    }
}