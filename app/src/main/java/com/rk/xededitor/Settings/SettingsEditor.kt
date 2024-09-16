package com.rk.xededitor.Settings

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.RelativeLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.R
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.editor.AutoSaver
import com.rk.xededitor.databinding.ActivitySettingsMainBinding
import com.rk.xededitor.rkUtils
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.onCheckedChange
import de.Maxr1998.modernpreferences.helpers.onClick
import de.Maxr1998.modernpreferences.helpers.pref
import de.Maxr1998.modernpreferences.helpers.screen
import de.Maxr1998.modernpreferences.helpers.switch

class SettingsEditor : BaseActivity() {
  
  private lateinit var recyclerView: RecyclerView
  private lateinit var binding: ActivitySettingsMainBinding
  private lateinit var padapter: PreferencesAdapter
  private lateinit var mLayoutManager: LinearLayoutManager
  
  private fun getRecyclerView(): RecyclerView {
    binding = ActivitySettingsMainBinding.inflate(layoutInflater)
    recyclerView = binding.recyclerView
    return recyclerView
  }
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    padapter = PreferencesAdapter(getScreen())
    
    savedInstanceState?.getParcelable<PreferencesAdapter.SavedState>("padapter")
      ?.let(padapter::loadSavedState)
    
    mLayoutManager = LinearLayoutManager(this)
    getRecyclerView().apply {
      layoutManager = mLayoutManager
      adapter = padapter
      //layoutAnimation = AnimationUtils.loadLayoutAnimation(this@settings2, R.anim.preference_layout_fall_down)
    }
    
    setContentView(binding.root)
    binding.toolbar.title = "Editor"
    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    if (SettingsData.isDarkMode(this) && SettingsData.isOled()) {
      binding.root.setBackgroundColor(Color.BLACK)
      binding.toolbar.setBackgroundColor(Color.BLACK)
      binding.appbar.setBackgroundColor(Color.BLACK)
      window.navigationBarColor = Color.BLACK
      val window = window
      window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
      window.statusBarColor = Color.BLACK
      window.navigationBarColor = Color.BLACK
    } else if (SettingsData.isDarkMode(this)) {
      val window = window
      window.navigationBarColor = Color.parseColor("#141118")
    }
    
  }
  
  private fun getScreen(): PreferenceScreen {
    return screen(this) {
      
      switch(Keys.VIEWPAGER_SMOOTH_SCROLL) {
        title = "Smooth Tabs"
        summary = "Smoothly switch between tabs"
        iconRes = R.drawable.animation
        defaultValue = true
        onCheckedChange { isChecked ->
          MainActivity.activityRef.get()?.smoothTabs = isChecked
          return@onCheckedChange true
        }
      }
      
      
      switch(Keys.WORD_WRAP_ENABLED) {
        titleRes = R.string.ww
        summary = "Enable Word Wrap in all editors"
        iconRes = R.drawable.reorder
        onCheckedChange { isChecked ->
          //todo
          return@onCheckedChange true
        }
      }
      
      switch(Keys.KEEP_DRAWER_LOCKED) {
        titleRes = R.string.keepdl
        summary = "Keep drawer locked when opening a file"
        iconRes = R.drawable.lock
      }
      
      switch(Keys.DIAGONAL_SCROLL) {
        title = "Diagonal Scrolling"
        summary = "Enable Diagonal Scrolling in File Browser"
        iconRes = R.drawable.diagonal_scroll
        defaultValue = false
        onCheckedChange {
          rkUtils.toast(this@SettingsEditor, "restart required")
          return@onCheckedChange true
        }
      }
      
      
      switch(Keys.CURSOR_ANIMATION_ENABLED) {
        title = "Cursor Animation"
        summary = "Enable Smooth Cursor Animations"
        iconRes = R.drawable.animation
        defaultValue = true
        onCheckedChange { isChecked ->
          //todo
          
          return@onCheckedChange true
        }
      }
      
      switch(Keys.SHOW_LINE_NUMBERS) {
        title = "Show Line Numbers"
        summary = "Show Line Numbers in Editor"
        iconRes = R.drawable.linenumbers
        defaultValue = true
        onCheckedChange { isChecked ->
          //todo
          return@onCheckedChange true
        }
      }
      
      switch(Keys.PIN_LINE_NUMBER) {
        title = "Pin Line Numbers"
        summary = "Pin Line Numbers in Editor"
        iconRes = R.drawable.linenumbers
        defaultValue = false
        onCheckedChange { isChecked ->
          //todo
          return@onCheckedChange true
        }
      }
      
      switch(Keys.SHOW_ARROW_KEYS) {
        title = "Extra Keys"
        summary = "Show extra keys in the editor"
        iconRes = R.drawable.double_arrows
        defaultValue = false
        onCheckedChange { isChecked ->
          MainActivity.activityRef.get()?.let { activity ->
            if (activity.tabViewModel.fragmentFiles.isEmpty()) {
              return@onCheckedChange true
            }
            LoadingPopup(this@SettingsEditor, 200)
            if (isChecked) {
              activity.binding.apply {
                divider.visibility = View.VISIBLE
                mainBottomBar.visibility = View.VISIBLE
              }
            } else {
              activity.binding.apply {
                divider.visibility = View.GONE
                mainBottomBar.visibility = View.GONE
              }
            }
            
            val viewpager = activity.binding.viewpager2
            val layoutParams = viewpager.layoutParams as RelativeLayout.LayoutParams
            layoutParams.bottomMargin = rkUtils.dpToPx(
              if (isChecked) {
                40f
              } else {
                0f
              }, activity
            )
            viewpager.setLayoutParams(layoutParams)
            
          }
          return@onCheckedChange true
        }
      }
      
      switch(Keys.USE_SPACE_INTABS) {
        title = "Use Space instead of Tabs"
        summary = "write whitespaces in place of tabs"
        iconRes = R.drawable.double_arrows
        defaultValue = true
      }
      
      switch(Keys.AUTO_SAVE) {
        title = "Auto Save"
        summary = "automatically save file"
        iconRes = R.drawable.save
        defaultValue = false
        onCheckedChange { isChecked ->
          //todo
          return@onCheckedChange true
        }
      }
      
      pref(Keys.AUTO_SAVE_TIME) {
        title = "Auto Save Time"
        summary = "automatically save file after specified time"
        iconRes = R.drawable.save
        onClick {
          val view =
            LayoutInflater.from(this@SettingsEditor).inflate(R.layout.popup_new, null)
          val edittext = view.findViewById<EditText>(R.id.name).apply {
            hint = "Interval in milliseconds"
            setText(SettingsData.getString(Keys.AUTO_SAVE_TIME_VALUE, "10000"))
            inputType =
              InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
          }
          MaterialAlertDialogBuilder(this@SettingsEditor).setTitle("Auto Save Time")
            .setView(view).setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
              val text = edittext.text.toString()
              for (c in text) {
                if (!c.isDigit()) {
                  rkUtils.toast(this@SettingsEditor, "invalid value")
                  return@setPositiveButton
                }
              }
              if (text.toInt() < 1000) {
                rkUtils.toast(this@SettingsEditor, "Value too small")
                return@setPositiveButton
              }
              
              
              SettingsData.setString(Keys.AUTO_SAVE_TIME_VALUE, text)
              AutoSaver.delayTime = text.toLong()
              
            }.show()
          
          
          
          return@onClick true
        }
      }
      
      pref(Keys.TEXT_SIZE) {
        title = "Text Size"
        summary = "Set text size"
        iconRes = R.drawable.reorder
        onClick {
          val view =
            LayoutInflater.from(this@SettingsEditor).inflate(R.layout.popup_new, null)
          val edittext = view.findViewById<EditText>(R.id.name).apply {
            hint = "Text size"
            setText(SettingsData.getString(Keys.TEXT_SIZE, "14"))
            inputType =
              InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
          }
          MaterialAlertDialogBuilder(this@SettingsEditor).setTitle("Text Size")
            .setView(view).setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
              val text = edittext.text.toString()
              for (c in text) {
                if (!c.isDigit()) {
                  rkUtils.toast(this@SettingsEditor, "invalid value")
                  return@setPositiveButton
                }
              }
              if (text.toInt() > 32) {
                rkUtils.toast(this@SettingsEditor, "Value too large")
                return@setPositiveButton
              }
              if (text.toInt() < 8) {
                rkUtils.toast(this@SettingsEditor, "Value too small")
                return@setPositiveButton
              }
              SettingsData.setString(Keys.TEXT_SIZE, text)
//todo
            
            
            }.show()
          
          return@onClick true
        }
      }
      
      pref(Keys.TAB_SIZE) {
        title = "Tab Size"
        summary = "Set tab size"
        iconRes = R.drawable.double_arrows
        onClick {
          val view =
            LayoutInflater.from(this@SettingsEditor).inflate(R.layout.popup_new, null)
          val edittext = view.findViewById<EditText>(R.id.name).apply {
            hint = "Tab Size"
            setText(SettingsData.getString(Keys.TAB_SIZE, "4"))
            inputType =
              InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
          }
          MaterialAlertDialogBuilder(this@SettingsEditor).setTitle("Tab Size")
            .setView(view).setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
              val text = edittext.text.toString()
              for (c in text) {
                if (!c.isDigit()) {
                  rkUtils.toast(this@SettingsEditor, "invalid value")
                  return@setPositiveButton
                }
              }
              if (text.toInt() > 16) {
                rkUtils.toast(this@SettingsEditor, "Value too large")
                return@setPositiveButton
              }
              
              SettingsData.setString(Keys.TAB_SIZE, text)
//todo
            
            }.show()
          
          return@onClick true
        }
      }
    }
  }
  
  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    // Save the padapter state as a parcelable into the Android-managed instance state
    outState.putParcelable("padapter", padapter.getSavedState())
  }
  
  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    // Handle action bar item clicks here
    val id = item.itemId
    if (id == android.R.id.home) {
      // Handle the back arrow click here
      onBackPressed()
      return true
    }
    return super.onOptionsItemSelected(item)
  }
}