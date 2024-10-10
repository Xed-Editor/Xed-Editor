package com.rk.libcommons

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//deprecated
class After(timeInMillis: Long, runnable: Runnable) {
  var scope:CoroutineScope = GlobalScope
  constructor(timeInMillis: Long, runnable: Runnable,scope:CoroutineScope) : this(timeInMillis,runnable){
    this.scope = scope
  }

  init {
    scope.launch(Dispatchers.Default){
      delay(timeInMillis)
      runnable.run()
    }
  }
}
