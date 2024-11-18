package com.rk.xededitor

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.rk.libcommons.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@Suppress("NOTHING_TO_INLINE")
object rkUtils {
    var mHandler = Handler(Looper.getMainLooper())
    
    inline fun runOnUiThread(runnable: Runnable) {
        mHandler.post(runnable)
    }
    
    inline fun isMainThread() = Thread.currentThread().name == "main"
    
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
    
    
    inline fun toast(message: String?) {
        runOnUiThread { Toast.makeText(App.app, message, Toast.LENGTH_SHORT).show() }
    }
    
    fun isLargeScreen(context: Context): Boolean {
        return (context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }
    
    fun isLandscape(context: Context): Boolean {
        val orientation = context.resources.configuration.orientation
        return orientation != Configuration.ORIENTATION_PORTRAIT
    }
    
    fun runCommandTermux(context: Context, exe: String, args: Array<String>, background: Boolean = true) {
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", exe)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
        }
        context.startForegroundService(intent)
    }
    
    fun isDesktopMode(context: Context): Boolean {
        return isLargeScreen(context) and isLandscape(context)
    }
    
    
    suspend inline fun Any.debug(string: String, tag: String? = null) {
        Log.d(tag ?: this.javaClass.simpleName, string)
        withContext(Dispatchers.IO) { File(application?.filesDir, "log.txt")
            .appendText("DEBUG: $string \n") }
    }
    
    suspend inline fun Any.error(string: String, tag: String? = null) {
        Log.e(tag ?: this.javaClass.simpleName, string)
        withContext(Dispatchers.IO) { File(application?.filesDir, "log.txt")
            .appendText("ERROR:  $string \n") }
    }
    
    suspend inline fun Any.warn(string: String, tag: String? = null) {
        Log.w(tag ?: this.javaClass.simpleName, string)
        withContext(Dispatchers.IO) { File(application?.filesDir, "log.txt")
            .appendText("WARN:  $string \n") }
    }
    
    inline fun Any.Debug(string: String, tag: String? = null) {
        Log.d(tag ?: this.javaClass.simpleName, string)
        GlobalScope.launch(Dispatchers.IO) { File(application?.filesDir, "log.txt")
            .appendText("DEBUG: $string \n") }
    }
    
    inline fun Any.Error(string: String, tag: String? = null) {
        Log.e(tag ?: this.javaClass.simpleName, string)
        GlobalScope.launch(Dispatchers.IO) { File(application?.filesDir, "log.txt")
            .appendText("ERROR:  $string \n") }
    }
    
    inline fun Any.Warn(string: String, tag: String? = null) {
        Log.w(tag ?: this.javaClass.simpleName, string)
        GlobalScope.launch(Dispatchers.IO) { File(application?.filesDir, "log.txt")
            .appendText("WARN:  $string \n") }
    }
    
    fun isPhysicalKeyboardConnected(context: Context): Boolean {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        
        val deviceIds = inputManager.inputDeviceIds
        for (id in deviceIds) {
            val inputDevice = inputManager.getInputDevice(id)
            if (inputDevice != null && inputDevice.sources == InputDevice.SOURCE_KEYBOARD) {
                return true
            }
        }
        return false
    }
    
    
    inline fun getString(stringId: Int): String {
        return ContextCompat.getString(App.app, stringId)
    }
    
    inline fun dpToPx(dp: Float, ctx: Context): Int {
        val density = ctx.resources.displayMetrics.density
        return Math.round(dp * density)
    }
    
    fun downloadFile(
        url: String,
        outputDir: String,
        fileName: String,
        onComplete: Runnable,
        onFailure: Runnable,
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
