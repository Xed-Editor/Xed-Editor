package com.rk.xededitor

class After(timeInMillis: Long, runnable: Runnable) {
  init {
    Thread{
      Thread.sleep(timeInMillis)
      runnable.run()
    }.start()
  }
}
