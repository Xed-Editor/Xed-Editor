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

class CrashHandler private constructor() : Thread.UncaughtExceptionHandler {
    private var context: Context? = null
    private val info: MutableMap<String, String> = HashMap()

    fun init(context: Context) {
        this.context = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        collectDeviceInfo(this.context)
        saveCrashInfo(thread.name, ex)

        if (Looper.myLooper() != null) {
            while (true) {
                try {
                    Looper.loop()
                    return // Quit loop if no exception
                } catch (t: Throwable) {
                    // process cannot be recovered, exit
                    exitProcess(1)
                }
            }
        }
    }

    private fun collectDeviceInfo(ctx: Context?) {
        val pi = ctx!!.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_ACTIVITIES)
        if (pi != null) {
            info["app_version"] = if (pi.versionName == null) "null" else pi.versionName
        }
        info["manufacturer"] = Build.MANUFACTURER
        info["brand"] = Build.BRAND
        info["device_model"] = Build.MODEL

        val sb = StringBuilder()
        for (abi in Build.SUPPORTED_ABIS) {
            sb.append(abi).append(',')
        }
        val abis = sb.toString()
        info["supported_abi"] = "[" + abis.substring(0, abis.length - 1) + "]"

        // save everything from Build.VERSION class
        val fields = Build.VERSION::class.java.declaredFields
        for (field in fields) {
            try {
                field.isAccessible = true
                val obj = field[null]
                if (obj is Array<*> && obj.isArrayOf<String>()) {
                    info[field.getName()] = obj.contentToString()
                } else {
                    if (obj != null) {
                        info[field.getName()] = obj.toString()
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "an error occurred while collecting crash info", e)
            }
        }
    }

    private fun saveCrashInfo(threadName: String, ex: Throwable) {
        // crash hander activity should never crash
        if (ex.stackTrace.toString().contains(CrashActivity::class.java.name)) {
            // if it does exit instantly or else the activity loop over and over
            exitProcess(1)
        } else {
            val sb = StringBuilder()
            for ((key, value) in info) {
                sb.append(key).append(" = ").append(value).append("\n")
            }
            val intent = Intent(context, CrashActivity::class.java)
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
            intent.putExtra("thread", threadName)
            intent.putExtra("info", sb.toString())
            context?.startActivity(intent)
        }
    }

    companion object {
        const val LOG_TAG = "CrashHandler"

        @SuppressLint("StaticFieldLeak") val INSTANCE = CrashHandler()
    }
}
