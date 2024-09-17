package com.rk.xededitor

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object rkUtils {
    private var mHandler = Handler(Looper.getMainLooper())

    fun runOnUiThread(runnable: Runnable) {
        mHandler.post(runnable)
    }

    fun shareText(ctx: Context, text: String?) {
        val sendIntent = Intent()
        sendIntent.setAction(Intent.ACTION_SEND)
        sendIntent.putExtra(Intent.EXTRA_TEXT, text)
        sendIntent.setType("text/plain")

        val shareIntent = Intent.createChooser(sendIntent, null)
        ctx.startActivity(shareIntent)
    }

    fun toast(context: Context?, message: String?) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    fun snackbar(rootView: View){
        Snackbar.make(rootView,"Text", Snackbar.LENGTH_SHORT).show()
    }

    fun dpToPx(dp: Float, ctx: Context): Int {
        val density = ctx.resources.displayMetrics.density
        return Math.round(dp * density)
    }

    fun took(runnable: Runnable): Long {
        val start = System.currentTimeMillis()
        runnable.run()
        return System.currentTimeMillis() - start
    }

    fun downloadFile(
        url: String, outputDir: String, fileName: String, onComplete: Runnable, onFailure: Runnable
    ) {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val file = File(outputDir, fileName)
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000 // 10 seconds
            connection.readTimeout = 10000 // 10 seconds
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.inputStream
                outputStream = FileOutputStream(file)
                val buffer = ByteArray(1024)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                onComplete.run()
            } else {
                onFailure.run()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            onFailure.run()
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            connection?.disconnect()
        }
    }
}
