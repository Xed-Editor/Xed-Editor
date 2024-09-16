package com.rk.libPlugin.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libPlugin.server.api.API

object PluginError : Thread.UncaughtExceptionHandler {
  
  fun showError(e:Exception){
    try {
      API.runOnUiThread{
        API.getMainActivity()
          ?.let { MaterialAlertDialogBuilder(it).setTitle("Error").setNeutralButton("Copy"){
              _, _ ->
            val clipboard = it.application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("label", e.toString())
            clipboard.setPrimaryClip(clip)
          }.setPositiveButton("OK", null).setMessage(e.message).show() }
      }
    }catch (e:Exception){
      e.printStackTrace()
    }
   
  }
  
  override fun uncaughtException(t: Thread, e: Throwable) {
    showError(Exception(e))
  }
}