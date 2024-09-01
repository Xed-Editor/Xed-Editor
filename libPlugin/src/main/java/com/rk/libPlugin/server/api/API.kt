package com.rk.libPlugin.server.api

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

//this class will be available to every plugin
object API {
    var application: Application? = null

    fun getInstance(): Any? {
         return try {
            val apiClass = API::class.java
            val instanceField = apiClass.getDeclaredField("INSTANCE").apply {
                isAccessible = true
            }
            instanceField.get(null)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getMainActivity(): Activity? {
        val companionField = Class.forName("com.rk.xededitor.BaseActivity").getDeclaredField("Companion").apply {
            isAccessible = true
        }
        val companionObject = companionField.get(null)

        val getActivityMethod =
            companionObject::class.java.getDeclaredMethod("getActivity", Class::class.java)


        return getActivityMethod.invoke(companionObject, Class.forName("com.rk.xededitor.MainActivity.MainActivity")) as Activity?
    }


    fun toast(message: String) {
        runOnUiThread {
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun runCommand(command: String): Command {
        return Command(command)
    }


    val handler = Handler(Looper.getMainLooper())
    fun runOnUiThread(runnable: Runnable?) {
        handler.post(runnable!!)
    }

    fun showPopup(title:String,message: String): AlertDialog? {
        var popup:AlertDialog? = null
        runOnUiThread{
           getMainActivity()?.let { popup =  MaterialAlertDialogBuilder(it).setTitle(title).setMessage(message).setPositiveButton("OK",null).show() }
        }
        return popup
    }

    fun showError(error:String){
        runOnUiThread{
            getMainActivity()?.let { MaterialAlertDialogBuilder(it).setTitle("Error").setNeutralButton("Copy"){
                    _, _ ->
                val clipboard = application!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("label", error)
                clipboard.setPrimaryClip(clip)
            }.setPositiveButton("OK", null).setMessage(error).show() }
        }
    }
}
