package com.rk.xededitor

import com.rk.xededitor.ui.themes.*;
import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.google.android.material.navigation.NavigationView
import com.rk.xededitor.databinding.ActivityMainBinding

import io.github.rosemoe.sora.widget.CodeEditor
import com.rk.xededitor.ui.home.HomeFragment;
import io.github.rosemoe.sora.*
import io.github.rosemoe.sora.widget.schemes.*
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
class MainActivity : AppCompatActivity() {
   
    private lateinit var mAppBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)
        
        val hbinding = HomeFragment.binding
        val editor = hbinding.editor
        
        if (rkUtils.isDarkMode(this)) {Xed_dark.applyTheme(this,editor)}else{Xed.applyTheme(this,editor)}

        val drawer: DrawerLayout = binding.drawerLayout
        val navigationView: NavigationView = binding.navView
        mAppBarConfiguration = AppBarConfiguration.Builder(R.id.nav_home)
            .setOpenableLayout(drawer)
            .build()
        val navController: NavController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main)
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration)
        NavigationUI.setupWithNavController(navigationView, navController)

       
        
        //continue in java
        //see Main.java
        Main(this,hbinding)
     
        
    }
    
    
    


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main)
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp()
    }

     
}

