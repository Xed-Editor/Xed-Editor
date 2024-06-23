package com.rk.xededitor

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Timer
import java.util.TimerTask

class After(timeInMillis: Long, runnable: Runnable) {
    init {
       Thread{
           Thread.sleep(timeInMillis)
           runnable.run()
       }.start()
    }
}
