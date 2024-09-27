package com.rk.xededitor.Settings

import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.libcommons.After
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.R
import com.rk.xededitor.databinding.ActivitySettingsMainBinding
import com.rk.xededitor.rkUtils
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.onCheckedChange
import de.Maxr1998.modernpreferences.helpers.screen
import de.Maxr1998.modernpreferences.helpers.switch

class SettingsApp : BaseActivity() {
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

        edgeToEdge(binding.root)
        setContentView(binding.root)

        binding.toggleButton.visibility = View.VISIBLE
        binding.toolbar.title = getString(R.string.app)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fun getCheckedBtnIdFromSettings(): Int {
            val settingDefaultNightMode = SettingsData.getString(
                Keys.DEFAULT_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString()
            ).toInt()

            return when (settingDefaultNightMode) {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> binding.auto.id
                AppCompatDelegate.MODE_NIGHT_NO -> binding.light.id
                AppCompatDelegate.MODE_NIGHT_YES -> binding.dark.id
                else -> throw RuntimeException("Illegal default night mode state")
            }
        }

        binding.toggleButton.check(getCheckedBtnIdFromSettings())

        val listener = View.OnClickListener {
            when (binding.toggleButton.checkedButtonId) {
                binding.auto.id -> {
                    LoadingPopup(this@SettingsApp, 200)
                    After(300) {
                        SettingsData.setString(
                            Keys.DEFAULT_NIGHT_MODE,
                            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString()
                        )

                        runOnUiThread {
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        }
                    }
                }

                binding.light.id -> {
                    LoadingPopup(this@SettingsApp, 200)
                    After(300) {
                        SettingsData.setString(
                            Keys.DEFAULT_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_NO.toString()
                        )

                        runOnUiThread {
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        }
                    }
                }

                binding.dark.id -> {
                    LoadingPopup(this@SettingsApp, 200)
                    After(300) {
                        SettingsData.setString(
                            Keys.DEFAULT_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_YES.toString()
                        )

                        runOnUiThread {
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        }
                    }
                }
            }
        }

        binding.light.setOnClickListener(listener)
        binding.dark.setOnClickListener(listener)
        binding.auto.setOnClickListener(listener)
    }

    private fun getScreen(): PreferenceScreen {
        return screen(this) {
            switch(Keys.OLED) {
                titleRes = R.string.oled
                summary = getString(R.string.oled_desc)
                iconRes = R.drawable.dark_mode
                defaultValue = false
                onCheckedChange {
                    LoadingPopup(this@SettingsApp, 180)
                    return@onCheckedChange true
                }
            }
            switch(Keys.MONET) {
                titleRes = R.string.monet
                summary = getString(R.string.monet_desc)
                iconRes = R.drawable.palette
                defaultValue = false
                onCheckedChange {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        rkUtils.toast("Unsupported Android version")
                        return@onCheckedChange false
                    }
                    LoadingPopup(this@SettingsApp, 180)
                    Toast.makeText(this@SettingsApp, getString(R.string.restart_app), 4000)
                        .show()
                    return@onCheckedChange true
                }
            }

            // R.I.P themes
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save the padapter state as a parcelable into the Android-managed instance state
        outState.putParcelable("padapter", padapter.getSavedState())
    }

    val Int.dp: Int
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics
        ).toInt()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here
        val id = item.itemId
        if (id == android.R.id.home) {
            // Handle the back arrow click here
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
