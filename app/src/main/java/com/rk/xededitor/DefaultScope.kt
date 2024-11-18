package com.rk.xededitor

import com.rk.libcommons.CustomScope

//same as MainActivity.lifeCycleScope

//this will get canceled on MainActivity.onDestroy
val DefaultScope = CustomScope()
