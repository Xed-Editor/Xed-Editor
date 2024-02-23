package com.rk.xededitor;

import android.os.*;
import android.view.WindowInsetsController;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.rk.xededitor.AndroidTreeView;
import com.rk.xededitor.rkUtils;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.rk.xededitor.rkUtils;
import com.rk.xededitor.ui.themes.*;
import android.os.*;
import android.net.*;
import android.content.*;
import android.provider.*;
import android.widget.*;
import android.app.*;
import android.view.*;
import android.widget.ExpandableListView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.*;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.navigation.NavigationView;
import com.rk.xededitor.databinding.ActivityMainBinding;
import io.github.rosemoe.sora.widget.CodeEditor;
import com.rk.xededitor.ui.home.HomeFragment;
import io.github.rosemoe.sora.*;
import io.github.rosemoe.sora.widget.schemes.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.w3c.dom.Document;

public class Settings extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_FOLDER = 123;

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
