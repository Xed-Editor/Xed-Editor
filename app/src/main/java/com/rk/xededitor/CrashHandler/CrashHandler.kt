package com.rk.xededitor.CrashHandler

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

@Suppress("NOTHING_TO_INLINE")
class CrashHandler private constructor() : Thread.UncaughtExceptionHandler {
    var applicationContext: Context? = null
    private val info: MutableMap<String, String> = HashMap()

    inline fun init(context: Context) {
        this.applicationContext = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        runCatching {
            val intent = Intent(applicationContext, CrashActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            var cause = ex.cause.toString()
            val prefix = "java.lang.Throwable:"
            if (cause.startsWith(prefix)) {
                cause = cause.removePrefix(prefix)
            }

            intent.putExtra("error_cause", cause)
            intent.putExtra("msg", ex.message)

            val stringWriter = StringWriter()
            val printWriter = PrintWriter(stringWriter)
            ex.printStackTrace(printWriter)
            val stackTraceString = stringWriter.toString()

            intent.putExtra("stacktrace", stackTraceString)
            intent.putExtra("thread", thread.name)

            applicationContext!!.startActivity(intent)


        }.onFailure {
            it.printStackTrace()
            exitProcess(1)
        }

        if (Looper.myLooper() != null) {
            while (true) {
                try {
                    Looper.loop()
                    return
                } catch (t: Throwable) {
                    //exitProcess(1)
                }
            }
        }
    }

    companion object {
        const val LOG_TAG = "CrashHandler"

        @SuppressLint("StaticFieldLeak") val INSTANCE = CrashHandler()
    }
}
