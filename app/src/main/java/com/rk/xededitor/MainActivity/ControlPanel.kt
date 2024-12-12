package com.rk.xededitor.MainActivity

import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showControlPanel(activity: MainActivity){
    MaterialAlertDialogBuilder(activity).apply {
        show()
    }
}