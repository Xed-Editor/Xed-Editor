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
import com.rk.resources.getString
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

    inline fun runOnUiThread(runnable: Runnable) {
        Handler(Looper.getMainLooper()).post(runnable)
    }

    inline fun toast(message: String?) {
        runOnUiThread { Toast.makeText(application!!, message.toString(), Toast.LENGTH_SHORT).show() }
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


    inline fun getString(stringId: Int): String {
        return stringId.getString()
    }

    inline fun dpToPx(dp: Float, ctx: Context): Int {
        val density = ctx.resources.displayMetrics.density
        return Math.round(dp * density)
    }
}
