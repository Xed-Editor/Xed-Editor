package com.rk.xededitor

import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.handlers.KeyEventHandler
import com.rk.xededitor.ui.theme.ThemeManager

@Keep
abstract class BaseActivity : AppCompatActivity() {

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null) {
            if (this::class.java.name == MainActivity::class.java.name){
                KeyEventHandler.onAppKeyEvent(event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
    }

    

    override fun onPause() {
        super.onPause()
        ThemeManager.apply(this)
    }

    
}
