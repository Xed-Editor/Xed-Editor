package com.rk.ai.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.rk.utils.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ClipboardService {

    suspend fun getClipboard(): String = withContext(Dispatchers.Main) {
        val app = application ?: return@withContext ""
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return@withContext ""
        cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
    }

    fun setClipboard(text: String) {
        val app = application ?: return
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("xed-agent", text))
    }
}
