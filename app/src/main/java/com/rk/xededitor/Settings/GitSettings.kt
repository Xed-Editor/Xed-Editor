package com.rk.xededitor.Settings

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.WindowManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.databinding.ActivitySettingsMainBinding
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.screen

class GitSettings : BaseActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var binding: ActivitySettingsMainBinding
    private lateinit var padapter: PreferencesAdapter
    private lateinit var playoutManager: LinearLayoutManager

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

        playoutManager = LinearLayoutManager(this)
        getRecyclerView().apply {
            layoutManager = playoutManager
            adapter = padapter
            //layoutAnimation = AnimationUtils.loadLayoutAnimation(this@settings2, R.anim.preference_layout_fall_down)
        }

        setContentView(binding.root)
        binding.toolbar.title = "Git"
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