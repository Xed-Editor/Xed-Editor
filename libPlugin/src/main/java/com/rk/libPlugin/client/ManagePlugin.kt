package com.rk.libPlugin.client

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rk.libPlugin.databinding.ActivityManageBinding
import com.rk.libPlugin.server.Server
import java.util.Objects

class ManagePlugin : AppCompatActivity() {
    lateinit var binding: ActivityManageBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageBinding.inflate(layoutInflater)

        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        Objects.requireNonNull(supportActionBar)?.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.title = "Manage Plugins"

        setContentView(binding.root)
        binding.listView.adapter = CustomListAdapter(this,Server.getInstalledPlugins())

    }
}