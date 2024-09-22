package com.rk.xededitor.Settings.v2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.rk.xededitor.ui.theme.KarbonTheme


class Settings : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      KarbonTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

        }
      }
    }
  }
}
