package com.rk.xededitor.Settings

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.databinding.ActivitySettingsMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.screen
import de.Maxr1998.modernpreferences.helpers.pref
import de.Maxr1998.modernpreferences.helpers.onClickView
import java.io.File
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
        binding.toolbar.title = getString(R.string.git)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
    }

    private fun getScreen(): PreferenceScreen {
        return screen(this) {
            pref(Keys.GIT_CREDENTIALS) {
                titleRes = R.string.cred
                summaryRes = R.string.gitcred
                iconRes = R.drawable.key
                onClickView {
                    val view = LayoutInflater.from(this@GitSettings).inflate(R.layout.popup_new, null)
                    val edittext = view.findViewById<EditText>(R.id.name).apply {
                        hint = getString(R.string.gitKeyExample)
                        setText(SettingsData.getString(Keys.GIT_CRED, ""))
                    }
                    MaterialAlertDialogBuilder(this@GitSettings).setTitle(getString(R.string.cred))
                        .setView(view).setNegativeButton(getString(R.string.cancel), null)
                        .setPositiveButton(getString(R.string.apply)) { _, _ ->
                            val credentials = edittext.text.toString()
                            if (credentials.isEmpty()) {
                                return@setPositiveButton
                            }
                            SettingsData.setString(Keys.GIT_CRED, credentials)
                        }.show()
                }
            }
            pref(Keys.GIT_USER) {
                titleRes = R.string.userdata
                summaryRes = R.string.userdatagit
                iconRes = R.drawable.person
                onClickView {
                    val view = LayoutInflater.from(this@GitSettings).inflate(R.layout.popup_new, null)
                    val edittext = view.findViewById<EditText>(R.id.name).apply {
                        hint = getString(R.string.gituserexample)
                        setText(SettingsData.getString(Keys.GIT_USER_DATA, ""))
                    }
                    MaterialAlertDialogBuilder(this@GitSettings).setTitle(getString(R.string.userdata))
                        .setView(view).setNegativeButton(getString(R.string.cancel), null)
                        .setPositiveButton(getString(R.string.apply)) { _, _ ->
                            val userdata = edittext.text.toString()
                            if (userdata.isEmpty()) {
                                return@setPositiveButton
                            }
                            SettingsData.setString(Keys.GIT_USER_DATA, userdata)
                        }.show()
                }
            }
            pref(Keys.GIT_DIR) {
                titleRes = R.string.repo_dir
                summaryRes = R.string.clone_dir
                iconRes = R.drawable.outline_folder_24
                onClickView {
                    val view = LayoutInflater.from(this@GitSettings).inflate(R.layout.popup_new, null)
                    val edittext = view.findViewById<EditText>(R.id.name).apply {
                        hint = "/storage/emulated/0"
                        setText(SettingsData.getString(Keys.GIT_REPO_DIR, "/storage/emulated/0"))
                    }
                    MaterialAlertDialogBuilder(this@GitSettings).setTitle(getString(R.string.repo_dir))
                        .setView(view).setNegativeButton(getString(R.string.cancel), null)
                        .setPositiveButton(getString(R.string.apply)) { _, _ ->
                            val repodir = edittext.text.toString()
                            if (repodir.isEmpty()) {
                                return@setPositiveButton
                            }
                            if (!File(repodir).exists()) {
                                rkUtils.toast(getString(R.string.dir_exist_not))
                                return@setPositiveButton
                            }
                            SettingsData.setString(Keys.GIT_REPO_DIR, repodir)
                        }.show()
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