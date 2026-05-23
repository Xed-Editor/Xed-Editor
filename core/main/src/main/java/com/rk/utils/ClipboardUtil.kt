package com.rk.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardUtil {
    private var contextRef: Context? = null

    fun initialize(context: Context) {
        contextRef = context.applicationContext
    }

    fun copy(text: CharSequence) {
        val ctx = contextRef ?: return
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText("label", text)
        clipboard.setPrimaryClip(clip)
    }

    fun paste(): String? {
        val ctx = contextRef ?: return null
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    }

    fun hasText(): Boolean {
        val ctx = contextRef ?: return false
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        return clipboard.hasPrimaryClip() && clipboard.primaryClip?.itemCount ?: 0 > 0
    }
}
