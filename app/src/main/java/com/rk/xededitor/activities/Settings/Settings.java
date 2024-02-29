package com.rk.xededitor;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.rk.xededitor.ui.themes.*;
import io.github.rosemoe.sora.*;
import io.github.rosemoe.sora.widget.schemes.*;

public class Settings extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_FOLDER = 123;
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Enable back button
        getSupportActionBar().setDisplayShowTitleEnabled(false); // Hide default title

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        if (!rkUtils.isDarkMode(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController insetsController = getWindow().getInsetsController();
                if (insetsController != null) {
                    insetsController.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decorView = getWindow().getDecorView();
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
        
        
        
        
        
        

        
    }

    
}
