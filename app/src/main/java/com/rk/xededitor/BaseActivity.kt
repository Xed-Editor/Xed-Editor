package com.rk.xededitor

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.theme.ThemeManager


abstract class BaseActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    ThemeManager.applyTheme(this)
    super.onCreate(savedInstanceState)

    if (!SettingsData.isDarkMode(this)) {
      //light mode
      window.navigationBarColor = Color.parseColor("#FEF7FF")
      val decorView = window.decorView
      var flags = decorView.systemUiVisibility
      flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
      decorView.systemUiVisibility = flags
      window.statusBarColor = Color.parseColor("#FEF7FF")
    }else{
window.statusBarColor = Color.parseColor("#141118")
}
    if (SettingsData.isDarkMode(this) && SettingsData.isOled(this)) {
      val window = window
      window.navigationBarColor = Color.BLACK
      window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
      window.statusBarColor = Color.BLACK
    }


  }

  override fun onPause() {
    super.onPause()
    ThemeManager.applyTheme(this)
  }
}