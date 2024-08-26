package com.rk.xededitor

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.rk.xededitor.MainActivity.StaticData
import io.github.rosemoe.sora.widget.CodeEditor

object rkUtils {
    private var mHandler = Handler(Looper.getMainLooper())

    fun runOnUiThread(runnable: Runnable) {
        mHandler.post(runnable)
    }

    val currentEditor: CodeEditor
        get() = StaticData.fragments[StaticData.mTabLayout.selectedTabPosition].editor

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

    fun dpToPx(dp: Float, ctx: Context): Int {
        val density = ctx.resources.displayMetrics.density
        return Math.round(dp * density)
    }

    fun took(runnable: Runnable): Long {
        val start = System.currentTimeMillis()
        runnable.run()
        return System.currentTimeMillis() - start
    }
}
