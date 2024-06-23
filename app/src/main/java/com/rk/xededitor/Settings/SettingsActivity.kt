package com.rk.xededitor.Settings

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.rk.xededitor.MainActivity.Data
import com.rk.xededitor.MainActivity.DynamicFragment
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import java.util.Objects


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setTitle("Settings")
        setSupportActionBar(toolbar)

        Objects.requireNonNull(supportActionBar)?.setDisplayShowTitleEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true) // for add back arrow in action bar
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
        if (!SettingsData.isDarkMode(this)) {
            //light mode
            window.navigationBarColor = Color.parseColor("#FEF7FF")
            val decorView = window.decorView
            var flags = decorView.systemUiVisibility
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            decorView.systemUiVisibility = flags
        }
        if (SettingsData.isDarkMode(this) && SettingsData.isOled(this)) {
            findViewById<View>(R.id.drawer_layout).setBackgroundColor(Color.BLACK)
            findViewById<View>(R.id.appbar).setBackgroundColor(Color.BLACK)
            findViewById<View>(R.id.toolbar).setBackgroundColor(Color.BLACK)
            window.navigationBarColor = Color.BLACK
            val window = window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.BLACK
        }




        Toggle(this, SettingsData.isOled(this))
            .setName("Black Night Theme")
            .setDrawable(R.drawable.dark_mode)
            .setListener { _, isChecked ->
                SettingsData.addToapplyPrefsOnRestart(
                    this@SettingsActivity,
                    "isOled",
                    isChecked.toString()
                )
                rkUtils.toast(this@SettingsActivity, "Setting will take effect after restart")
            }.showToggle()



        Toggle(this, SettingsData.getBoolean(this, "wordwrap", false))
            .setName("Word wrap")
            .setDrawable(R.drawable.reorder)
            .setListener { _, isChecked ->
                SettingsData.setBoolean(this@SettingsActivity, "wordwrap", isChecked)
                if (Data.fragments != null && Data.fragments.isNotEmpty()) {
                    for (fragment in Data.fragments) {
                        val dynamicFragment = fragment as DynamicFragment
                        dynamicFragment.editor.isWordwrap = isChecked
                    }
                    rkUtils.toast("Please wait for word wrap to complete")

                }
            }.showToggle()



        Toggle(this, SettingsData.getBoolean(this, "antiWordBreaking", true))
            .setName("Anti word breaking for (WW)")
            .setDrawable(R.drawable.reorder)
            .setListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
                SettingsData.setBoolean(
                    this@SettingsActivity,
                    "antiWordBreaking",
                    isChecked
                )
                if (Data.fragments == null) {
                    return@OnCheckedChangeListener
                }
                for (fragment in Data.fragments) {
                    val dynamicFragment = fragment as DynamicFragment
                    dynamicFragment.editor.setWordwrap(
                        SettingsData.getBoolean(
                            this@SettingsActivity,
                            "wordwrap",
                            false
                        ), SettingsData.getBoolean(this@SettingsActivity, "antiWordBreaking", true)
                    )
                }
            }).showToggle()


        Toggle(this, SettingsData.getBoolean(this, "keepDrawerLocked", false))
            .setName("Keep Drawer Locked")
            .setDrawable(R.drawable.lock)
            .setListener { _, isChecked ->
                SettingsData.setBoolean(
                    this@SettingsActivity,
                    "keepDrawerLocked",
                    isChecked
                )
            }.showToggle()



        Toggle(this, SettingsData.getBoolean(this, "useIcu", false))
            .setName("use libICU")
            .setDrawable(R.drawable.reorder)
            .setListener { _, isChecked ->
                SettingsData.setBoolean(this@SettingsActivity, "useIcu", isChecked)

                if (Data.fragments != null) {
                    for (dynamicFragment in Data.fragments) {
                        dynamicFragment.editor.props.useICULibToSelectWords = isChecked
                    }
                }


            }.showToggle()


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