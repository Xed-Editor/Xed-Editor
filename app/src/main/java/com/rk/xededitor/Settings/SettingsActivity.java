package com.rk.xededitor.Settings;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.rk.xededitor.MainActivity.DynamicFragment;
import com.rk.xededitor.MainActivity.mAdapter;
import com.rk.xededitor.R;
import com.rk.xededitor.rkUtils;

import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {
    private SettingsActivity activity;
    private LayoutInflater inflater;

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

        inflater = LayoutInflater.from(this);


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

        mainBody.addView(addSwitch("Black Night Theme", rkUtils.isOled(this),R.drawable.dark_mode, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                rkUtils.addToapplyPrefsOnRestart(SettingsActivity.this, "isOled", Boolean.toString(isChecked));
                rkUtils.toast(SettingsActivity.this, "Setting will take effect after restart");
            }
        }));
        mainBody.addView(addSwitch("Word Wrap", Boolean.parseBoolean(rkUtils.getSetting(activity, "wordwrap", "false")),R.drawable.reorder, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                rkUtils.setSetting(activity, "wordwrap", Boolean.toString(isChecked));
                if (mAdapter.fragments == null) {
                    return;
                }
                for (Fragment fragment : mAdapter.fragments) {
                    DynamicFragment dynamicFragment = (DynamicFragment) fragment;
                    dynamicFragment.editor.setWordwrap(Boolean.parseBoolean(rkUtils.getSetting(activity, "wordwrap", "false")));
                }
            }
        }));

        mainBody.addView(addSwitch("Anti word breaking for (WW)", Boolean.parseBoolean(rkUtils.getSetting(this, "antiWordBreaking", "true")),R.drawable.reorder, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                rkUtils.setSetting(SettingsActivity.this, "antiWordBreaking", Boolean.toString(isChecked));
                if (mAdapter.fragments == null) {
                    return;
                }
                for (Fragment fragment : mAdapter.fragments) {
                    DynamicFragment dynamicFragment = (DynamicFragment) fragment;
                    dynamicFragment.editor.setWordwrap(Boolean.parseBoolean(rkUtils.getSetting(activity, "wordwrap", "false")), Boolean.parseBoolean(rkUtils.getSetting(activity, "antiWordBreaking", "true")));
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

    public View addSwitch(String name, boolean enabled, CompoundButton.OnCheckedChangeListener listener) {
        return addSwitch(name, enabled, R.drawable.settings, listener);
    }

    public View addSwitch(String name, boolean enabled, int resourceId, CompoundButton.OnCheckedChangeListener listener) {
        View v = inflater.inflate(R.layout.setting_toggle_layout, null);
        TextView textView = v.findViewById(R.id.textView);
        MaterialSwitch materialSwitch = v.findViewById(R.id.materialSwitch);
        materialSwitch.setChecked(enabled);
        textView.setText(name);
        materialSwitch.setOnCheckedChangeListener(listener);
        ImageView imageView = v.findViewById(R.id.imageView);
        imageView.setImageDrawable(ContextCompat.getDrawable(this,resourceId));
        return v;
    }


}