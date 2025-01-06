package com.rk.xededitor

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.application
import com.rk.resources.strings
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.git.GitClient
import com.rk.xededitor.rkUtils.toastIt
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
        runOnUiThread { Toast.makeText(application!!, message.toString(), Toast.LENGTH_SHORT).show() }
    }

    fun isLargeScreen(context: Context): Boolean {
        return (context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    fun isLandscape(context: Context): Boolean {
        val orientation = context.resources.configuration.orientation
        return orientation != Configuration.ORIENTATION_PORTRAIT
    }


    inline fun String?.toastIt(){
        toast(this)
    }

    data class PopupButton(val label:String, val listener:(()->Unit)? = null)


    fun Activity.askInput(title: String, message: String, onResult:(String)->Unit){
        val popupView: View = LayoutInflater.from(this).inflate(R.layout.popup_new, null)
        val editText = popupView.findViewById<EditText>(R.id.name)
        editText.hint = message

        MaterialAlertDialogBuilder(this).apply {
            setTitle(title)
            setView(popupView)
            var dialog: androidx.appcompat.app.AlertDialog? = null
            setNegativeButton(strings.cancel,null)
            setPositiveButton(strings.ok) { _, _ ->
                dialog?.dismiss()
                onResult.invoke(editText.text.toString())
            }
            dialog = show()
        }

    }

    fun Activity.askSomething(title:String,message: String,buttons:Pair<PopupButton?,PopupButton?>?){
        runOnUiThread {
            var dialog: androidx.appcompat.app.AlertDialog? = null
            MaterialAlertDialogBuilder(this).apply {
                setTitle(title)
                setMessage(message)
                buttons?.first?.let {
                    setNegativeButton(buttons.first?.label) { _, _ ->
                        dialog?.dismiss()
                        buttons.first?.listener?.invoke()
                    }
                }

                buttons?.second?.let {
                    setPositiveButton(buttons.second?.label) { _, _ ->
                        dialog?.dismiss()
                        buttons.second?.listener?.invoke()
                    }
                }
                dialog = show()
            }
        }
    }

    fun isDesktopMode(context: Context): Boolean {
        return isLargeScreen(context) and isLandscape(context)
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
        return ContextCompat.getString(application!!, stringId)
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
