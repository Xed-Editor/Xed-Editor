package com.rk.xededitor.theme

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.widget.Toolbar
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.CircleCheckBox
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.databinding.ThemesActivityBinding
import com.rk.xededitor.rkUtils

class ThemeActivity : BaseActivity() {
  private lateinit var binding: ThemesActivityBinding


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ThemeManager.applyTheme(this)
    binding = ThemesActivityBinding.inflate(layoutInflater)
    setContentView(binding.root)

    val toolbar: Toolbar = binding.toolbar
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar!!.title = "Themes"
    
    rkUtils.ni(this)

    if (SettingsData.isDarkMode(this) && SettingsData.isOled(this)) {
      binding.root.setBackgroundColor(Color.BLACK)
      binding.appbar.setBackgroundColor(Color.BLACK)
      binding.mainBody.setBackgroundColor(Color.BLACK)
      binding.toolbar.setBackgroundColor(Color.BLACK)
      window.navigationBarColor = Color.BLACK
      val window = window
      window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
      window.statusBarColor = Color.BLACK
      window.navigationBarColor = Color.BLACK
    }

    val themes = ThemeManager.getThemes(this)

    val list = ArrayList<Pair<CircleCheckBox,Int>>()

    themes.forEach { theme ->
      CircleCheckBox(this).apply {
        id = View.generateViewId()
        setPadding(40.dp, paddingTop, paddingRight, paddingBottom)
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, 60.dp
        )
        borderThickness = 2.dp.toFloat()
        setCircleBorderColorHex("#00CFAD")
        setInnerCircleColorHex("#00CFAD")
        innerCircleRadius = 14.dp.toFloat()
        setOuterCircleColorHex("#6400CFAD")
        outerCircleRadius = 6.dp.toFloat()
        isShowOuterCircle = true
        textLeftPadding = 5.dp.toFloat()
        textSize = 16.dp.toFloat()
        setTickColorHex("#ffffff")
        tickThickness = 1.dp.toFloat()
        text = theme.first

        setOnCheckedChangeListener { view, isChecked ->
          if (isChecked) {
            list.forEach { checkBox ->
              if (checkBox != view && checkBox.first.checked) {
                checkBox.first.checked = false
              }
            }

          }
        }

        binding.mainBody.addView(this)
        list.add(Pair(this,theme.second))
      }
    }



  }

  val Int.dp: Int
    get() = TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics
    ).toInt()

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.theme_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    // Handle action bar item clicks here
    val id = item.itemId
    if (id == android.R.id.home) {
      // Handle the back arrow click here
      onBackPressed()
      return true
    } else if (id == R.id.save) {

    }
    return super.onOptionsItemSelected(item)
  }

}