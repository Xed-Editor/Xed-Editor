package com.rk.crashhandler

import android.content.Intent
import android.os.Looper
import com.rk.libcommons.application
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

object CrashHandler : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        runCatching {
            val intent = Intent(application!!, CrashActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

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

            application!!.startActivity(intent)
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
                    Thread{
                        t.printStackTrace()
                        logErrorOrExit(t)
                    }.start()
                }
            }
        }
    }
}

fun logErrorOrExit(throwable: Throwable){
    runCatching {
        application!!.filesDir.child("crash.log").createFileIfNot().appendText(throwable.toString())
    }.onFailure { it.printStackTrace();exitProcess(-1) }
}
