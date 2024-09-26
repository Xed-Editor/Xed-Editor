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
import com.rk.xededitor.MainActivity.tabFragments
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
    edgeToEdge()
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
    
    binding.toolbar.title = getString(R.string.editor)
    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
  }
  
  private fun getScreen(): PreferenceScreen {
    return screen(this) {
      
      switch(Keys.VIEWPAGER_SMOOTH_SCROLL) {
        title = getString(R.string.smooth_tabs)
        summary = getString(R.string.smooth_tab_desc)
        iconRes = R.drawable.animation
        defaultValue = true
        onCheckedChange { isChecked ->
          MainActivity.activityRef.get()?.smoothTabs = isChecked
          return@onCheckedChange true
        }
      }
      
      
      switch(Keys.WORD_WRAP_ENABLED) {
        titleRes = R.string.ww
        summary = getString(R.string.ww_desc)
        iconRes = R.drawable.reorder
        onCheckedChange { isChecked ->
          tabFragments.forEach{ f ->
            f.value.editor?.isWordwrap = isChecked
          }
          return@onCheckedChange true
        }
      }
      
      switch(Keys.KEEP_DRAWER_LOCKED) {
        titleRes = R.string.keepdl
        summaryRes = R.string.drawer_lock_desc
        iconRes = R.drawable.lock
      }
      
      switch(Keys.DIAGONAL_SCROLL) {
        titleRes = R.string.diagonal_scroll
        summaryRes = R.string.diagonal_scroll_desc
        iconRes = R.drawable.diagonal_scroll
        defaultValue = false
        onCheckedChange {
          rkUtils.toast(getString(R.string.rr))
          return@onCheckedChange true
        }
      }
      
      
      switch(Keys.CURSOR_ANIMATION_ENABLED) {
        titleRes = R.string.cursor_anim
        summaryRes = R.string.cursor_anim_desc
        iconRes = R.drawable.animation
        defaultValue = true
        onCheckedChange { isChecked ->
          tabFragments.forEach{ f ->
            f.value.editor?.isCursorAnimationEnabled = isChecked
          }
          
          return@onCheckedChange true
        }
      }
      
      switch(Keys.SHOW_LINE_NUMBERS) {
        titleRes = R.string.show_line_number
        summaryRes = R.string.show_line_number
        iconRes = R.drawable.linenumbers
        defaultValue = true
        onCheckedChange { isChecked ->
          tabFragments.forEach{ f ->
            f.value.editor?.isLineNumberEnabled = isChecked
          }
          return@onCheckedChange true
        }
      }
      
      switch(Keys.PIN_LINE_NUMBER) {
        titleRes = R.string.pin_line_number
        summaryRes = R.string.pin_line_number
        iconRes = R.drawable.linenumbers
        defaultValue = false
        onCheckedChange { isChecked ->
          tabFragments.forEach{ f ->
            f.value.editor?.setPinLineNumber(isChecked)
          }
          return@onCheckedChange true
        }
      }
      
      switch(Keys.SHOW_ARROW_KEYS) {
        titleRes = R.string.extra_keys
        summaryRes = R.string.extra_keys_desc
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
      
      switch(Keys.AUTO_SAVE) {
        titleRes = R.string.auto_save
        summaryRes = R.string.auto_save_desc
        iconRes = R.drawable.save
        defaultValue = false
        onCheckedChange { isChecked ->
          if (isChecked){
            MainActivity.activityRef.get()?.let { AutoSaver.start(it) }
          }else{
            AutoSaver.stop()
          }

          return@onCheckedChange true
        }
      }
      
      pref(Keys.AUTO_SAVE_TIME) {
        titleRes = R.string.auto_save_desc
        summaryRes = R.string.auto_save_time_desc
        iconRes = R.drawable.save
        onClick {
          val view =
            LayoutInflater.from(this@SettingsEditor).inflate(R.layout.popup_new, null)
          val edittext = view.findViewById<EditText>(R.id.name).apply {
            hint = getString(R.string.intervalinMs)
            setText(SettingsData.getString(Keys.AUTO_SAVE_TIME_VALUE, "10000"))
            inputType =
              InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
          }
          MaterialAlertDialogBuilder(this@SettingsEditor).setTitle(getString(R.string.auto_save_time))
            .setView(view).setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
              val text = edittext.text.toString()
              for (c in text) {
                if (!c.isDigit()) {
                  rkUtils.toast(getString(R.string.inavalid_v))
                  return@setPositiveButton
                }
              }
              if (text.toInt() < 1000) {
                rkUtils.toast(getString(R.string.v_small))
                return@setPositiveButton
              }
              
              
              SettingsData.setString(Keys.AUTO_SAVE_TIME_VALUE, text)
              AutoSaver.delayTime = text.toLong()
              
            }.show()
          
          
          
          return@onClick true
        }
      }
      
      pref(Keys.TEXT_SIZE) {
        titleRes = R.string.text_size
        summaryRes = R.string.text_size_desc
        iconRes = R.drawable.reorder
        onClick {
          val view =
            LayoutInflater.from(this@SettingsEditor).inflate(R.layout.popup_new, null)
          val edittext = view.findViewById<EditText>(R.id.name).apply {
            hint = getString(R.string.text_size)
            setText(SettingsData.getString(Keys.TEXT_SIZE, "14"))
            inputType =
              InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
          }
          MaterialAlertDialogBuilder(this@SettingsEditor).setTitle(getString(R.string.text_size))
            .setView(view).setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
              val text = edittext.text.toString()
              for (c in text) {
                if (!c.isDigit()) {
                  rkUtils.toast(getString(R.string.inavalid_v))
                  return@setPositiveButton
                }
              }
              if (text.toInt() > 32) {
                rkUtils.toast(getString(R.string.v_large))
                return@setPositiveButton
              }
              if (text.toInt() < 8) {
                rkUtils.toast(getString(R.string.v_small))
                return@setPositiveButton
              }
              SettingsData.setString(Keys.TEXT_SIZE, text)
              tabFragments.forEach{ f ->
                f.value.editor?.setTextSize(text.toFloat())
              }
            
            }.show()
          
          return@onClick true
        }
      }
      
      pref(Keys.TAB_SIZE) {
        titleRes = R.string.tab_size
        summaryRes = R.string.tab_size_desc
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
          MaterialAlertDialogBuilder(this@SettingsEditor).setTitle(getString(R.string.tab_size))
            .setView(view).setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
              val text = edittext.text.toString()
              for (c in text) {
                if (!c.isDigit()) {
                  rkUtils.toast(getString(R.string.inavalid_v))
                  return@setPositiveButton
                }
              }
              if (text.toInt() > 16) {
                rkUtils.toast(getString(R.string.v_large))
                return@setPositiveButton
              }
              
              SettingsData.setString(Keys.TAB_SIZE, text)

              tabFragments.forEach{ f ->
                f.value.editor?.tabWidth = text.toInt()
              }
            
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