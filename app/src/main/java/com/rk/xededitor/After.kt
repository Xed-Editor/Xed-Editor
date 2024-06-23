package com.rk.xededitor

import java.util.Timer
import java.util.TimerTask

class After(timeInMillis:Long, runnable: Runnable) {
    init {
        val timer = Timer()
        val timerTask: TimerTask = object : TimerTask() {
            override fun run() {
               runnable.run()
            }
        }
        timer.schedule(timerTask,0, timeInMillis)
    }
}