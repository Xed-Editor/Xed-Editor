package com.rk.xededitor.Settings

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.WindowManager
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jaredrummler.ktsh.Shell
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.R
import com.rk.xededitor.databinding.ActivitySettingsMainBinding
import com.rk.xededitor.rkUtils
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.onClick
import de.Maxr1998.modernpreferences.helpers.onClickView
import de.Maxr1998.modernpreferences.helpers.pref
import de.Maxr1998.modernpreferences.helpers.screen
import de.Maxr1998.modernpreferences.helpers.switch
import java.io.File
import java.nio.file.Files

class TerminalSettings : BaseActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var binding: ActivitySettingsMainBinding
    lateinit var padapter: PreferencesAdapter
    lateinit var playoutManager: LinearLayoutManager


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
        binding.toolbar.title = "Terminal"
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
            switch(Keys.FAIL_SAFE) {
                title = "Fail safe"
                summary = "use android shell instead of alpine"
                iconRes = R.drawable.android
            }
            pref(Keys.START_SHELL_PREF) {
                title = "Launch Shell"
                summary = "Executed when the terminal is launched."
                iconRes = R.drawable.terminal
                onClickView {

                    val view =
                        LayoutInflater.from(this@TerminalSettings).inflate(R.layout.popup_new, null)
                    val edittext = view.findViewById<EditText>(R.id.name).apply {
                        hint = "eg. /bin/sh"
                        setText(
                            SettingsData.getString(Keys.SHELL, "/bin/sh")
                                .removePrefix(File(filesDir.parentFile, "rootfs/").absolutePath)
                        )
                    }
                    MaterialAlertDialogBuilder(this@TerminalSettings).setTitle("Launch Shell")
                        .setView(view).setNegativeButton("Cancel", null)
                        .setPositiveButton("Apply") { _, _ ->
                            val shell = edittext.text.toString()

                            if (shell.isEmpty()) {
                                return@setPositiveButton
                            }

                            //verify shell exists
                            val absoluteShell = File(filesDir.parentFile, "rootfs/$shell")
                            if (absoluteShell.exists()
                                    .not() && !Files.isSymbolicLink(absoluteShell.toPath())
                            ) {
                                rkUtils.toast(this@TerminalSettings, "File does not exist")
                                return@setPositiveButton
                            }

                            SettingsData.setString(Keys.SHELL, absoluteShell.absolutePath)

                            Shell.SH.run("echo \"$shell\" > ${filesDir.parentFile!!.absolutePath}/shell")


                        }.show()
                }
            }

            pref(Keys.TERMINAL_TEXT_SIZE_PREF){
                title = "Terminal text size"
                summary = "Set terminal text size"
                iconRes = R.drawable.terminal
                onClick {
                    val view =
                        LayoutInflater.from(this@TerminalSettings).inflate(R.layout.popup_new, null)
                    val edittext = view.findViewById<EditText>(R.id.name).apply {
                        hint = "Terminal Text size"
                        setText(SettingsData.getString(Keys.TERMINAL_TEXT_SIZE, "14"))
                        inputType =
                            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    }
                    MaterialAlertDialogBuilder(this@TerminalSettings).setTitle("Text Size")
                        .setView(view).setNegativeButton("Cancel", null)
                        .setPositiveButton("Apply") { _, _ ->
                            val text = edittext.text.toString()
                            for (c in text) {
                                if (!c.isDigit()) {
                                    rkUtils.toast(this@TerminalSettings,"invalid value")
                                    return@setPositiveButton
                                }
                            }
                            if (text.toInt() > 32) {
                                rkUtils.toast(this@TerminalSettings,"Value too large")
                                return@setPositiveButton
                            }

                            if (text.toInt() < 8){
                                rkUtils.toast(this@TerminalSettings,"Value too small")
                                return@setPositiveButton
                            }
                            SettingsData.setString(Keys.TERMINAL_TEXT_SIZE, text)

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