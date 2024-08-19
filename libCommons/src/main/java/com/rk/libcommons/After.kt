package com.rk.libcommons

import android.os.Handler
import android.os.Looper

class After(timeInMillis: Long, runnable: Runnable) {
  init {
    Thread {
      Thread.sleep(timeInMillis)
      runnable.run()
    }.start()
  }
}

