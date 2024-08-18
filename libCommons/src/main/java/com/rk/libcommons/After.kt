package com.rk.libcommons

class After(timeInMillis: Long, runnable: Runnable) {
  init {
    Thread {
      Thread.sleep(timeInMillis)
      runnable.run()
    }.start()
  }
}
