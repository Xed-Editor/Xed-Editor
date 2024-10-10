package com.rk.plugin.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.plugin.server.api.API

object PluginError {
    fun showError(e: Exception) {
        try {
            API.runOnUiThread {
                val activity = API.getActivityContext()
                if (activity == null) {
                    e.printStackTrace()
                    return@runOnUiThread
                }
                activity.let {
                    MaterialAlertDialogBuilder(it)
                        .setTitle("Error")
                        .setNeutralButton("Copy") { _, _ ->
                            val clipboard =
                                it.application.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                            val clip = ClipData.newPlainText("label", e.stackTraceToString())
                            clipboard.setPrimaryClip(clip)
                        }
                        .setPositiveButton("OK", null)
                        .setMessage(e.stackTraceToString())
                        .show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
