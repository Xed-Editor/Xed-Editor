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
import com.rk.xededitor.BaseActivity
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.MainActivity.fragment.AutoSaver
import com.rk.xededitor.MainActivity.fragment.DynamicFragment
import com.rk.xededitor.R
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

            switch(SettingsData.Keys.WORD_WRAP_ENABLED) {
                titleRes = R.string.ww
                summary = "Enable Word Wrap in all editors"
                iconRes = R.drawable.reorder
                onCheckedChange { isChecked ->
                    if (StaticData.fragments != null && StaticData.fragments.isNotEmpty()) {
                        for (fragment in StaticData.fragments) {
                            val dynamicFragment = fragment as DynamicFragment
                            dynamicFragment.editor.isWordwrap = isChecked
                        }
                    }
                    return@onCheckedChange true
                }
            }

            switch(SettingsData.Keys.KEEP_DRAWER_LOCKED) {
                titleRes = R.string.keepdl
                summary = "Keep drawer locked when opening a file"
                iconRes = R.drawable.lock
                onCheckedChange { isChecked ->
                    return@onCheckedChange true
                }
            }

            switch(SettingsData.Keys.DIAGONAL_SCROLL) {
                title = "Diagnol Scrolling"
                summary = "Enable Diagnol Scrolling in File Browser"
                iconRes = R.drawable.diagonal_scroll
                defaultValue = false
                onCheckedChange {
                    LoadingPopup(this@SettingsEditor,180)
                    getActivity(MainActivity::class.java)?.recreate()
                    return@onCheckedChange true
                }
            }

            switch(SettingsData.Keys.CURSOR_ANIMATION_ENABLED) {
                title = "Cursor Animation"
                summary = "Enable Smooth Cursor Animations"
                iconRes = R.drawable.animation
                defaultValue = true
                onCheckedChange { isChecked ->
                    StaticData.fragments?.forEach { f ->
                        f.editor.isCursorAnimationEnabled = isChecked
                    }

                    return@onCheckedChange true
                }
            }

            switch(SettingsData.Keys.SHOW_LINE_NUMBERS) {
                title = "Show Line Numbers"
                summary = "Show Line Numbers in Editor"
                iconRes = R.drawable.linenumbers
                defaultValue = true
                onCheckedChange { isChecked ->
                    if (StaticData.fragments?.isNotEmpty() == true) {
                        StaticData.fragments.forEach { fragment ->
                            fragment.editor.isLineNumberEnabled = isChecked
                        }
                    }
                    return@onCheckedChange true
                }
            }

            switch(SettingsData.Keys.PIN_LINE_NUMBER) {
                title = "Pin Line Numbers"
                summary = "Pin Line Numbers in Editor"
                iconRes = R.drawable.linenumbers
                defaultValue = false
                onCheckedChange { isChecked ->
                    if (StaticData.fragments?.isNotEmpty() == true) {
                        StaticData.fragments.forEach { fragment ->
                            fragment.editor.setPinLineNumber(isChecked)
                        }
                    }
                    return@onCheckedChange true
                }
            }

            switch(SettingsData.Keys.SHOW_ARROW_KEYS) {
                title = "Extra Keys"
                summary = "Show extra keys in the editor"
                iconRes = R.drawable.double_arrows
                defaultValue = false
                onCheckedChange { isChecked ->
                    if (StaticData.fragments == null || StaticData.fragments.isEmpty()) {
                        return@onCheckedChange true
                    }
                    LoadingPopup(this@SettingsEditor,200)

                    if (isChecked) {
                        getActivity(MainActivity::class.java)?.binding?.divider?.visibility =
                            View.VISIBLE
                        getActivity(MainActivity::class.java)?.binding?.mainBottomBar?.visibility =
                            View.VISIBLE
                        val vp = getActivity(MainActivity::class.java)?.binding?.viewpager
                        val layoutParams = vp?.layoutParams as RelativeLayout.LayoutParams
                        layoutParams.bottomMargin = rkUtils.dpToPx(
                            40f, getActivity(MainActivity::class.java)!!
                        ) // Convert dp to pixels as needed
                        vp.setLayoutParams(layoutParams)
                    } else {
                        getActivity(MainActivity::class.java)?.binding?.divider?.visibility =
                            View.GONE
                        getActivity(MainActivity::class.java)?.binding?.mainBottomBar?.visibility =
                            View.GONE
                        val vp = getActivity(MainActivity::class.java)?.binding?.viewpager
                        val layoutParams = vp?.layoutParams as RelativeLayout.LayoutParams
                        layoutParams.bottomMargin = rkUtils.dpToPx(
                            0f, getActivity(MainActivity::class.java)!!
                        ) // Convert dp to pixels as needed
                        vp.setLayoutParams(layoutParams)
                    }

                    getActivity(MainActivity::class.java)?.recreate()

                    return@onCheckedChange true
                }
            }

            switch(SettingsData.Keys.USE_SPACE_INTABS) {
                title = "Use Space instead of Tabs"
                summary = "write whitespaces in place of tabs"
                iconRes = R.drawable.double_arrows
                defaultValue = true
            }

            switch(SettingsData.Keys.AUTO_SAVE){
                title = "Auto Save"
                summary = "automatically save file"
                iconRes = R.drawable.save
                defaultValue = false
                onCheckedChange {
                    AutoSaver()
                    return@onCheckedChange true
                }
            }

            pref(SettingsData.Keys.AUTO_SAVE_TIME){
                title = "Auto Save Time"
                summary = "automatically save file after specified time"
                iconRes = R.drawable.save
                onClick {
                    val view =
                        LayoutInflater.from(this@SettingsEditor).inflate(R.layout.popup_new, null)
                    val edittext = view.findViewById<EditText>(R.id.name).apply {
                        hint = "Interval in milliseconds"
                        setText(SettingsData.getString(SettingsData.Keys.AUTO_SAVE_TIME_VALUE, "2000"))
                        inputType =
                            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    }
                    MaterialAlertDialogBuilder(this@SettingsEditor).setTitle("Auto Save Time")
                        .setView(view).setNegativeButton("Cancel", null)
                        .setPositiveButton("Apply") { dialog, which ->
                            val text = edittext.text.toString()
                            for (c in text) {
                                if (!c.isDigit()) {
                                    rkUtils.toast(this@SettingsEditor,"invalid value")
                                    return@setPositiveButton
                                }
                            }
                            if (text.toInt() < 1000) {
                                rkUtils.toast(this@SettingsEditor,"Value too small")
                                return@setPositiveButton
                            }
                            SettingsData.setString(SettingsData.Keys.AUTO_SAVE_TIME_VALUE, text)

                            AutoSaver.setIntervalMillis(text.toLong())

                        }.show()



                    return@onClick true
                }
            }

            pref(SettingsData.Keys.TEXT_SIZE) {
                title = "Text Size"
                summary = "Set text size"
                iconRes = R.drawable.reorder
                onClick {
                    val view =
                        LayoutInflater.from(this@SettingsEditor).inflate(R.layout.popup_new, null)
                    val edittext = view.findViewById<EditText>(R.id.name).apply {
                        hint = "Text size"
                        setText(SettingsData.getString(SettingsData.Keys.TEXT_SIZE, "14"))
                        inputType =
                            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    }
                    MaterialAlertDialogBuilder(this@SettingsEditor).setTitle("Text Size")
                        .setView(view).setNegativeButton("Cancel", null)
                        .setPositiveButton("Apply") { dialog, which ->
                            val text = edittext.text.toString()
                            for (c in text) {
                                if (!c.isDigit()) {
                                    rkUtils.toast(this@SettingsEditor,"invalid value")
                                    return@setPositiveButton
                                }
                            }
                            if (text.toInt() > 32) {
                                rkUtils.toast(this@SettingsEditor,"Value too large")
                                return@setPositiveButton
                            }
                            SettingsData.setString(SettingsData.Keys.TEXT_SIZE, text)

                            if (StaticData.fragments != null) {
                                for (f in StaticData.fragments) {
                                    f.editor.setTextSize(text.toFloat())
                                }
                            }


                        }.show()

                    return@onClick true
                }
            }

            pref(SettingsData.Keys.TAB_SIZE) {
                title = "Tab Size"
                summary = "Set tab size"
                iconRes = R.drawable.double_arrows
                onClick {
                    val view =
                        LayoutInflater.from(this@SettingsEditor).inflate(R.layout.popup_new, null)
                    val edittext = view.findViewById<EditText>(R.id.name).apply {
                        hint = "Tab Size"
                        setText(SettingsData.getString(SettingsData.Keys.TAB_SIZE, "4"))
                        inputType =
                            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    }
                    MaterialAlertDialogBuilder(this@SettingsEditor).setTitle("Tab Size")
                        .setView(view).setNegativeButton("Cancel", null)
                        .setPositiveButton("Apply") { dialog, which ->
                            val text = edittext.text.toString()
                            for (c in text) {
                                if (!c.isDigit()) {
                                    rkUtils.toast(this@SettingsEditor,"invalid value")
                                    return@setPositiveButton
                                }
                            }
                            if (text.toInt() > 16) {
                                rkUtils.toast(this@SettingsEditor,"Value too large")
                                return@setPositiveButton
                            }
                            SettingsData.setString(SettingsData.Keys.TAB_SIZE, text)

                            if (StaticData.fragments != null) {
                                for (f in StaticData.fragments) {
                                    f.editor.tabWidth = text.toInt()
                                }
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