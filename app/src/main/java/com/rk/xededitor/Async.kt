package com.rk.xededitor

import java.util.concurrent.*

class Async {
  companion object{
    @JvmStatic
    fun run(runnable: Runnable){
      Thread(runnable).start()
    }
  }
}
