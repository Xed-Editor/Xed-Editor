package com.rk.xededitor.Settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.R
import com.rk.xededitor.databinding.ActivitySettingsMainBinding
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.onClickView
import de.Maxr1998.modernpreferences.helpers.pref
import de.Maxr1998.modernpreferences.helpers.screen

class SettingsMainActivity : BaseActivity() {
  private lateinit var recyclerView: RecyclerView
  private lateinit var binding: ActivitySettingsMainBinding
  lateinit var padapter: PreferencesAdapter
  lateinit var playoutManager: LinearLayoutManager

  companion object {
    var settingsMain: SettingsMainActivity? = null
  }

  private fun getRecyclerView(): RecyclerView {
    binding = ActivitySettingsMainBinding.inflate(layoutInflater)
    recyclerView = binding.recyclerView
    return recyclerView
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    settingsMain = this
    edgeToEdge()
    super.onCreate(savedInstanceState)
    padapter = PreferencesAdapter(getScreen())
    savedInstanceState?.getParcelable<PreferencesAdapter.SavedState>("padapter")
      ?.let(padapter::loadSavedState)

    playoutManager = LinearLayoutManager(this)
    getRecyclerView().apply {
      layoutManager = playoutManager
      adapter = padapter
      //layoutAnimation = AnimationUtils.loadLayoutAnimation(this@settings2, R.anim.preference_layout_fall_down)
    }


    setContentView(binding.root)
    binding.toolbar.title = "Settings"
    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
  }

  override fun onDestroy() {
    settingsMain = null
    super.onDestroy()
  }

  private fun getScreen(): PreferenceScreen {
    return screen(this) {
      pref(Keys.APPLICATION) {
        title = "Application"
        summary = "General settings for the application"
        iconRes = R.drawable.android
        onClickView {
          startActivity(Intent(this@SettingsMainActivity, SettingsApp::class.java))
        }
      }
      pref(Keys.EDITOR) {
        title = "Editor"
        summary = "General settings for the editor"
        iconRes = R.drawable.edit
        onClickView {
          startActivity(Intent(this@SettingsMainActivity, SettingsEditor::class.java))
        }
      }
      pref(Keys.PLUGINS) {
        title = "Plugins"
        summary = "General settings for plugins"
        iconRes = R.drawable.extension
        onClickView {
          startActivity(Intent(this@SettingsMainActivity, SettingsPlugins::class.java))
        }
      }

      pref(Keys.TERMINAL) {
        title = "Terminal"
        summary = "General settings for terminal"
        iconRes = R.drawable.terminal
        onClickView {
          startActivity(Intent(this@SettingsMainActivity, TerminalSettings::class.java))
        }
      }

      pref(Keys.GIT) {
        title = "Git"
        summary = "General settings for git"
        iconRes = R.drawable.git
        onClickView {
          startActivity(Intent(this@SettingsMainActivity, GitSettings::class.java))
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