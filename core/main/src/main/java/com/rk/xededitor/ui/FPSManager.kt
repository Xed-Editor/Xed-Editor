package com.rk.xededitor.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceControl
import android.view.WindowManager
import com.rk.libcommons.toast

class FPSBooster(activity: Activity) {
    init {
        with(activity){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                window.frameRateBoostOnTouchEnabled = true
                window.isFrameRatePowerSavingsBalanced = false
            }

            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            }

            display?.let {
                val highestMode = it.supportedModes.maxByOrNull { mode -> mode.refreshRate }
                highestMode?.let { mode ->
                    val lp = window.attributes
                    lp.preferredDisplayModeId = mode.modeId
                    lp.preferredRefreshRate = mode.refreshRate
                    window.attributes = lp
                }
            }

        }

    }
}