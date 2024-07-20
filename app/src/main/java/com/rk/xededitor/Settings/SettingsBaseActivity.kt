package com.rk.xededitor.Settings

import android.os.Bundle
import android.view.MenuItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.BaseActivity
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.PreferencesAdapter

abstract class SettingsBaseActivity : BaseActivity() {
  private lateinit var padapter: PreferencesAdapter
  private lateinit var playoutManager: LinearLayoutManager
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    padapter = PreferencesAdapter(getScreen())
    
    savedInstanceState?.getParcelable<PreferencesAdapter.SavedState>("padapter")
      ?.let(padapter::loadSavedState)
    
    playoutManager = LinearLayoutManager(this)
    get_recycler_view().apply {
      layoutManager = playoutManager
      adapter = padapter
      //layoutAnimation = AnimationUtils.loadLayoutAnimation(this@settings2, R.anim.preference_layout_fall_down)
    }
    
  }
  
  abstract fun getScreen(): PreferenceScreen
  abstract fun get_recycler_view(): RecyclerView
  
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