package com.rk.xededitor

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys

object rkUtils {
  private var mHandler = Handler(Looper.getMainLooper())

  fun runOnUiThread(runnable: Runnable) {
    mHandler.post(runnable)
  }

  fun shareText(ctx: Context, text: String?) {
    try {
      val sendIntent = Intent()
      sendIntent.setAction(Intent.ACTION_SEND)
      sendIntent.putExtra(Intent.EXTRA_TEXT, text)
      sendIntent.setType("text/plain")
      val shareIntent = Intent.createChooser(sendIntent, null)
      ctx.startActivity(shareIntent)
    } catch (e: Exception) {
      e.printStackTrace()
      toast("error : ${e.printStackTrace()}")
    }

  }

  fun toast(message: String?) {
    runOnUiThread {
      Toast.makeText(App.app, message, Toast.LENGTH_SHORT).show()
    }
  }

  fun isLargeScreen(context: Context): Boolean {
    return (context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
  }
  fun isLandscape(context: Context): Boolean {
    val orientation = context.resources.configuration.orientation
    return orientation != Configuration.ORIENTATION_PORTRAIT
  }
  fun isDesktopMode(context: Context):Boolean{
    return PreferencesData.getBoolean(PreferencesKeys.FORCE_DESKTOP_MODE,false) || (isLargeScreen(context) and isLandscape(context))
  }

  fun debug(string: String,tag:String = "rkUtils"){
    Log.d(tag,string)
  }
  fun error(string: String,tag:String = "rkUtils"){
    Log.e(tag,string)
  }
  fun warn(string: String,tag:String = "rkUtils"){
    Log.w(tag,string)
  }




  fun getString(stringId: Int): String {
    return ContextCompat.getString(App.app, stringId)
  }

  fun dpToPx(dp: Float, ctx: Context): Int {
    val density = ctx.resources.displayMetrics.density
    return Math.round(dp * density)
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
