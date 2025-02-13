package com.rk.libcommons

import android.app.Activity
import android.app.Application
import java.lang.ref.WeakReference

@JvmField
var application:Application? = null


var currentActivity = WeakReference<Activity?>(null)

