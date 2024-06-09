package com.rk.xededitor.Settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.rk.xededitor.MainActivity.DynamicFragment;
import com.rk.xededitor.MainActivity.mAdapter;
import com.rk.xededitor.R;
import com.rk.xededitor.rkUtils;

import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {
    private SettingsActivity activity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Settings");
        setSupportActionBar(toolbar);
        activity = this;
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // for add back arrow in action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }


        if (!rkUtils.isDarkMode(this)) {
            //light mode
            getWindow().setNavigationBarColor(Color.parseColor("#FEF7FF"));
            View decorView = getWindow().getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decorView.setSystemUiVisibility(flags);
        }
        if (rkUtils.isDarkMode(this) && rkUtils.isOled(this)) {
            findViewById(R.id.drawer_layout).setBackgroundColor(Color.BLACK);
            findViewById(R.id.appbar).setBackgroundColor(Color.BLACK);
            findViewById(R.id.toolbar).setBackgroundColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.BLACK);
        }
        LinearLayout mainBody = findViewById(R.id.mainBody);


        //switches







        mainBody.addView(addSwitch("Black Night Theme", rkUtils.isOled(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean("isOled", isChecked);
                editor.apply();
                rkUtils.toast(activity, "Restart Required");
            }
        }));
        mainBody.addView(addSwitch("Word Wrap", Boolean.parseBoolean(rkUtils.getSetting(activity,"wordwrap","false")), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                rkUtils.setSetting(activity,"wordwrap",Boolean.toString(isChecked));
                if(mAdapter.fragments == null){
                    return;
                }
                for(Fragment fragment : mAdapter.fragments){
                    DynamicFragment dynamicFragment = (DynamicFragment) fragment;
                    dynamicFragment.editor.setWordwrap(Boolean.parseBoolean(rkUtils.getSetting(activity,"wordwrap","false")));
                }
            }
        }));

        mainBody.addView(addSwitch("Anti word breaking for (WW)", Boolean.parseBoolean(rkUtils.getSetting(this,"antiWordBreaking","true")), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                rkUtils.setSetting(SettingsActivity.this,"antiWordBreaking",Boolean.toString(isChecked));
                if(mAdapter.fragments == null){
                    return;
                }
                for(Fragment fragment : mAdapter.fragments){
                    DynamicFragment dynamicFragment = (DynamicFragment) fragment;
                    dynamicFragment.editor.setWordwrap(Boolean.parseBoolean(rkUtils.getSetting(activity,"wordwrap","false")),Boolean.parseBoolean(rkUtils.getSetting(activity,"antiWordBreaking","true")));
                }
            }
        }));



    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // Handle the back arrow click here
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public View addSwitch(String name, boolean enabled, CompoundButton.OnCheckedChangeListener listner) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setPadding(10, 0, 10, 0); // Padding in pixels, not dp
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Create a TextView
        TextView textView = new TextView(this);
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        textView.setText(name);

        // Create a MaterialSwitch
        MaterialSwitch materialSwitch = new MaterialSwitch(this);

        materialSwitch.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        materialSwitch.setChecked(enabled);
        materialSwitch.setId(View.generateViewId());
        materialSwitch.setOnCheckedChangeListener(listner);

        // Add views to LinearLayout
        linearLayout.addView(textView);
        linearLayout.addView(materialSwitch);

        // Set the LinearLayout as the content view
        return linearLayout;

    }

}