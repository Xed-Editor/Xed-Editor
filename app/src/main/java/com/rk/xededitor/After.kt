package com.rk.xededitor

class After(timeInMillis: Long, runnable: Runnable) {
  init {
    Async.run{
      Thread.sleep(timeInMillis)
      runnable.run()
    }
  }
}
