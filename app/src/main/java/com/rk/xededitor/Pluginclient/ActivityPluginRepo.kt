package com.rk.xededitor.Pluginclient

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.databinding.ActivityPluginsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActivityPluginRepo : BaseActivity() {
    lateinit var binding: ActivityPluginsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPluginsBinding.inflate(layoutInflater)

        binding.fab.hide()
        binding.listView.visibility = View.GONE
        binding.scrollview.visibility = View.VISIBLE

        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.title = "Plugin Repo"

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

        setContentView(binding.root)


        val xadapter = RepoPluginAdapter()
        
        binding.recyclerView.apply {
            adapter = xadapter
            layoutManager = LinearLayoutManager(this@ActivityPluginRepo)
            itemAnimator = null
        }

        RepoManager.getPluginsCallback { plugin ->
            xadapter.submitList(xadapter.currentList.toMutableList().apply {
                add(plugin)
            })
        }


    }

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