package com.rk.runner

import android.app.Activity
import android.content.Context
import com.rk.icons.Icon

interface RunnableOption {
    val label: String
    fun getIcon(context: Context): Icon?
    fun run(activity: Activity)
}
